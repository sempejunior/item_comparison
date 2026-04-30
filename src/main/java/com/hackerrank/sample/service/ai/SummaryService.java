package com.hackerrank.sample.service.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hackerrank.sample.model.Category;
import com.hackerrank.sample.model.CompareItem;
import com.hackerrank.sample.model.DifferenceEntry;
import com.hackerrank.sample.model.Language;
import com.hackerrank.sample.model.insights.RankingEntry;
import com.hackerrank.sample.model.insights.TopItem;
import com.hackerrank.sample.service.insights.Picks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.web.client.HttpStatusCodeException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Generates the optional natural-language {@code summary} fields exposed by
 * the {@code /compare} (per-pair comparison) and {@code /category-insights}
 * (whole-category landscape) endpoints (SPEC-004 + SPEC-005).
 *
 * <p>Every fallback path returns {@link Optional#empty()} and is recorded
 * via {@link AiMetrics}. The HTTP endpoints must remain a 200 even when the
 * LLM is unavailable.</p>
 */
@Service
public class SummaryService {

    private static final Logger log = LoggerFactory.getLogger(SummaryService.class);

    static final String CACHE_NAME = "ai-summary";
    static final String CACHE_NAME_INSIGHTS = "ai-category-insights";
    static final String PROMPT_TEMPLATE = "compare-summary.v2.md";
    static final String PROMPT_VERSION_COMPARE = "v2";
    static final String PROMPT_TEMPLATE_INSIGHTS = "category-insights.v3.md";
    static final String PROMPT_VERSION_INSIGHTS = "v3";
    private static final String DISABLED_KEY = "disabled";
    private static final String OPENAI_KEY_ENV = "OPENAI_API_KEY";

    private final PromptLoader promptLoader;
    private final AiMetrics metrics;
    private final DailyBudget budget;
    private final CacheManager cacheManager;
    private final ObjectProvider<ChatModel> chatModelProvider;
    private final ObjectMapper objectMapper;
    private final long timeoutMs;
    private final Supplier<String> apiKeyResolver;

    private final AtomicBoolean keyInvalidForBoot = new AtomicBoolean(false);

    @Autowired
    public SummaryService(
            PromptLoader promptLoader,
            AiMetrics metrics,
            DailyBudget budget,
            CacheManager cacheManager,
            ObjectProvider<ChatModel> chatModelProvider,
            ObjectMapper objectMapper,
            @Value("${app.ai.timeout-ms:2000}") long timeoutMs) {
        this(promptLoader, metrics, budget, cacheManager, chatModelProvider, objectMapper, timeoutMs,
                () -> System.getenv(OPENAI_KEY_ENV));
    }

    SummaryService(
            PromptLoader promptLoader,
            AiMetrics metrics,
            DailyBudget budget,
            CacheManager cacheManager,
            ObjectProvider<ChatModel> chatModelProvider,
            ObjectMapper objectMapper,
            long timeoutMs,
            Supplier<String> apiKeyResolver) {
        this.promptLoader = promptLoader;
        this.metrics = metrics;
        this.budget = budget;
        this.cacheManager = cacheManager;
        this.chatModelProvider = chatModelProvider;
        this.objectMapper = objectMapper;
        this.timeoutMs = timeoutMs;
        this.apiKeyResolver = apiKeyResolver;
    }

    public Optional<String> summarise(List<CompareItem> items, List<DifferenceEntry> differences, Language language) {
        Objects.requireNonNull(items, "items");
        Objects.requireNonNull(differences, "differences");
        Objects.requireNonNull(language, "language");

        String key = compareCacheKey(items, differences, language);
        return runLlm(
                AiMetrics.KIND_SUMMARY,
                CACHE_NAME,
                key,
                () -> renderComparePrompt(items, differences, language));
    }

    public Optional<String> summariseCategoryInsights(
            Category category,
            int productCount,
            List<RankingEntry> rankings,
            List<TopItem> topItems,
            Picks picks,
            Language language) {
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(rankings, "rankings");
        Objects.requireNonNull(topItems, "topItems");
        Objects.requireNonNull(language, "language");

        String key = insightsCacheKey(category, productCount, rankings, topItems, picks, language);
        return runLlm(
                AiMetrics.KIND_INSIGHTS,
                CACHE_NAME_INSIGHTS,
                key,
                () -> renderInsightsPrompt(category, productCount, rankings, topItems, picks, language));
    }

    private Optional<String> runLlm(String kind, String cacheName, String key, PromptRenderer renderer) {
        if (!hasUsableApiKey()) {
            return fallback(kind, AiMetrics.REASON_NO_KEY);
        }

        Cache cache = cacheManager.getCache(cacheName);
        Optional<String> cached = readCached(kind, cache, key);
        if (cached.isPresent()) {
            return cached;
        }

        if (!budget.tryConsume()) {
            return fallback(kind, AiMetrics.REASON_BUDGET);
        }

        ChatModel chatModel = chatModelProvider.getIfAvailable();
        if (chatModel == null) {
            return fallback(kind, AiMetrics.REASON_NO_KEY);
        }

        String renderedPrompt;
        try {
            renderedPrompt = renderer.render();
        } catch (JsonProcessingException ex) {
            log.warn("Failed to serialise LLM payload (kind={}): {}", kind, ex.getMessage());
            return fallback(kind, AiMetrics.REASON_EXCEPTION);
        }

        return invokeAndStore(kind, chatModel, renderedPrompt, cache, key);
    }

    private boolean hasUsableApiKey() {
        return resolveApiKey() != null && !keyInvalidForBoot.get();
    }

    private Optional<String> readCached(String kind, Cache cache, String key) {
        if (cache == null) {
            return Optional.empty();
        }
        String hit = cache.get(key, String.class);
        if (hit == null) {
            return Optional.empty();
        }
        metrics.recordOutcome(kind, AiMetrics.OUTCOME_CACHE_HIT);
        return Optional.of(hit);
    }

    private Optional<String> invokeAndStore(String kind, ChatModel chatModel, String renderedPrompt, Cache cache, String key) {
        long startedAt = System.nanoTime();
        try {
            ChatResponse response = invokeWithTimeout(chatModel, renderedPrompt);
            recordTokens(kind, response.getMetadata() == null ? null : response.getMetadata().getUsage());
            metrics.recordLatency(kind, Duration.ofNanos(System.nanoTime() - startedAt));
            String content = extractContent(response);
            if (content == null || content.isBlank()) {
                return fallback(kind, AiMetrics.REASON_EXCEPTION);
            }
            String summary = content.trim();
            if (cache != null) {
                cache.put(key, summary);
            }
            metrics.recordOutcome(kind, AiMetrics.OUTCOME_OK);
            return Optional.of(summary);
        } catch (TimeoutException ex) {
            metrics.recordLatency(kind, Duration.ofNanos(System.nanoTime() - startedAt));
            metrics.recordOutcome(kind, AiMetrics.OUTCOME_TIMEOUT);
            metrics.recordFallback(AiMetrics.REASON_TIMEOUT);
            return Optional.empty();
        } catch (Throwable ex) {
            metrics.recordLatency(kind, Duration.ofNanos(System.nanoTime() - startedAt));
            String reason = classifyError(ex);
            if (AiMetrics.REASON_AUTH.equals(reason)) {
                keyInvalidForBoot.set(true);
            }
            metrics.recordOutcome(kind, AiMetrics.OUTCOME_FALLBACK);
            metrics.recordFallback(reason);
            log.warn("LLM fallback (kind={}, reason={}): {}", kind, reason, ex.getMessage());
            return Optional.empty();
        }
    }

    private Optional<String> fallback(String kind, String reason) {
        metrics.recordOutcome(kind, AiMetrics.OUTCOME_FALLBACK);
        metrics.recordFallback(reason);
        return Optional.empty();
    }

    boolean isKeyInvalidForBoot() {
        return keyInvalidForBoot.get();
    }

    private ChatResponse invokeWithTimeout(ChatModel chatModel, String renderedPrompt) throws Throwable {
        Prompt prompt = new Prompt(new UserMessage(renderedPrompt));
        CompletableFuture<ChatResponse> future = CompletableFuture.supplyAsync(() -> chatModel.call(prompt));
        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (ExecutionException ex) {
            throw ex.getCause() == null ? ex : ex.getCause();
        } catch (TimeoutException ex) {
            future.cancel(true);
            throw ex;
        }
    }

    private String resolveApiKey() {
        String value = apiKeyResolver.get();
        if (value == null || value.isBlank() || DISABLED_KEY.equalsIgnoreCase(value.trim())) {
            return null;
        }
        return value.trim();
    }

    private String renderComparePrompt(List<CompareItem> items, List<DifferenceEntry> differences, Language language)
            throws JsonProcessingException {
        Map<String, String> bindings = new LinkedHashMap<>();
        bindings.put("language", language.tag());
        bindings.put("products", objectMapper.writeValueAsString(slimItems(items)));
        bindings.put("differences", objectMapper.writeValueAsString(differences));
        bindings.put("wins", objectMapper.writeValueAsString(buildWins(items, differences)));
        return promptLoader.render(PROMPT_TEMPLATE, bindings);
    }

    static Map<String, List<String>> buildWins(List<CompareItem> items, List<DifferenceEntry> differences) {
        Map<String, List<String>> wins = new LinkedHashMap<>();
        for (CompareItem item : items) {
            if (item.id() != null) {
                wins.put(String.valueOf(item.id()), new ArrayList<>());
            }
        }
        for (DifferenceEntry diff : differences) {
            if (diff.winnerId() == null || diff.values() == null) {
                continue;
            }
            Object value = diff.values().get(diff.winnerId());
            if (value == null) {
                continue;
            }
            List<String> bucket = wins.get(String.valueOf(diff.winnerId()));
            if (bucket == null) {
                continue;
            }
            bucket.add(labelOf(diff.path()) + ": " + String.valueOf(value));
        }
        return wins;
    }

    private static String labelOf(String path) {
        if (path == null || path.isEmpty()) {
            return "value";
        }
        int dot = path.lastIndexOf('.');
        return dot < 0 ? path : path.substring(dot + 1);
    }

    private String renderInsightsPrompt(
            Category category,
            int productCount,
            List<RankingEntry> rankings,
            List<TopItem> topItems,
            Picks picks,
            Language language) throws JsonProcessingException {
        Map<String, String> bindings = new LinkedHashMap<>();
        bindings.put("language", language.tag());
        bindings.put("category", category.name());
        bindings.put("productCount", Integer.toString(productCount));
        bindings.put("rankings", objectMapper.writeValueAsString(slimRankings(rankings)));
        bindings.put("topItems", objectMapper.writeValueAsString(slimTopItems(topItems)));
        bindings.put("picks", picks == null ? "null" : objectMapper.writeValueAsString(picks));
        return promptLoader.render(PROMPT_TEMPLATE_INSIGHTS, bindings);
    }

    private List<Map<String, Object>> slimItems(List<CompareItem> items) {
        return items.stream().map(item -> {
            Map<String, Object> slim = new LinkedHashMap<>();
            slim.put("id", item.id());
            slim.put("name", item.name());
            slim.put("buyBox", item.buyBox());
            slim.put("attributes", item.attributes());
            return slim;
        }).collect(Collectors.toList());
    }

    private List<Map<String, Object>> slimRankings(List<RankingEntry> rankings) {
        return rankings.stream().map(r -> {
            Map<String, Object> slim = new LinkedHashMap<>();
            slim.put("path", r.path());
            slim.put("isComparable", r.isComparable());
            if (r.winner() != null) {
                Map<String, Object> w = new LinkedHashMap<>();
                w.put("name", r.winner().name());
                w.put("value", r.winner().value());
                slim.put("winner", w);
            }
            if (r.runnerUp() != null) {
                Map<String, Object> ru = new LinkedHashMap<>();
                ru.put("name", r.runnerUp().name());
                ru.put("value", r.runnerUp().value());
                slim.put("runnerUp", ru);
            }
            if (r.spread() != null) {
                slim.put("spread", r.spread());
            }
            return slim;
        }).collect(Collectors.toList());
    }

    private List<Map<String, Object>> slimTopItems(List<TopItem> topItems) {
        return topItems.stream().map(t -> {
            Map<String, Object> slim = new LinkedHashMap<>();
            slim.put("name", t.name());
            slim.put("price", t.price());
            slim.put("rating", t.rating());
            return slim;
        }).collect(Collectors.toList());
    }

    private String compareCacheKey(List<CompareItem> items, List<DifferenceEntry> differences, Language language) {
        String ids = items.stream()
                .map(CompareItem::id)
                .filter(Objects::nonNull)
                .sorted()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
        return PROMPT_VERSION_COMPARE + "|" + language.tag() + "|" + ids + "|" + differences.hashCode();
    }

    private String insightsCacheKey(
            Category category,
            int productCount,
            List<RankingEntry> rankings,
            List<TopItem> topItems,
            Picks picks,
            Language language) {
        return PROMPT_VERSION_INSIGHTS + "|" + language.tag() + "|" + category.name()
                + "|" + productCount + "|" + rankings.hashCode() + "|" + topItems.hashCode()
                + "|" + (picks == null ? "0" : picks.hashCode());
    }

    private String extractContent(ChatResponse response) {
        if (response == null) {
            return null;
        }
        Generation generation = response.getResult();
        if (generation == null || generation.getOutput() == null) {
            return null;
        }
        return generation.getOutput().getContent();
    }

    private void recordTokens(String kind, Usage usage) {
        if (usage == null) {
            return;
        }
        Long prompt = usage.getPromptTokens();
        Long completion = usage.getGenerationTokens();
        if (prompt != null) {
            metrics.recordTokens(kind, AiMetrics.DIRECTION_IN, prompt);
        }
        if (completion != null) {
            metrics.recordTokens(kind, AiMetrics.DIRECTION_OUT, completion);
        }
    }

    private String classifyError(Throwable ex) {
        HttpStatusCodeException httpEx = findHttpStatusCause(ex);
        if (httpEx != null) {
            int status = httpEx.getStatusCode().value();
            if (status == 401 || status == 403) {
                return AiMetrics.REASON_AUTH;
            }
            if (status >= 500) {
                return AiMetrics.REASON_SERVER_ERROR;
            }
            if (status >= 400) {
                return AiMetrics.REASON_CLIENT_ERROR;
            }
        }
        String message = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase(Locale.ROOT);
        if (message.contains("401") || message.contains("unauthorized") || message.contains("invalid api key")) {
            return AiMetrics.REASON_AUTH;
        }
        if (message.contains(" 5") || message.contains("server error") || message.contains("internal_server_error")) {
            return AiMetrics.REASON_SERVER_ERROR;
        }
        if (message.contains("400") || message.contains("404") || message.contains("429") || message.contains("client error")) {
            return AiMetrics.REASON_CLIENT_ERROR;
        }
        return AiMetrics.REASON_EXCEPTION;
    }

    private static HttpStatusCodeException findHttpStatusCause(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof HttpStatusCodeException httpEx) {
                return httpEx;
            }
            if (current.getCause() == current) {
                return null;
            }
            current = current.getCause();
        }
        return null;
    }

    @FunctionalInterface
    private interface PromptRenderer {
        String render() throws JsonProcessingException;
    }
}
