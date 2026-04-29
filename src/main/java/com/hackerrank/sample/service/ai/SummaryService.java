package com.hackerrank.sample.service.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hackerrank.sample.model.CompareItem;
import com.hackerrank.sample.model.DifferenceEntry;
import com.hackerrank.sample.model.Language;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
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

import java.time.Duration;
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
 * Generates the optional natural-language {@code summary} field that
 * accompanies a deterministic comparison response (SPEC-004).
 *
 * <p>Every fallback path returns {@link Optional#empty()} and is recorded
 * via {@link AiMetrics}. The compare endpoint must remain a 200 even when
 * the LLM is unavailable — that contract lives in SPEC-003 §2.3.</p>
 */
@Service
public class SummaryService {

    private static final Logger log = LoggerFactory.getLogger(SummaryService.class);

    static final String CACHE_NAME = "ai-summary";
    static final String PROMPT_TEMPLATE = "compare-summary.v1.md";
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

        String apiKey = resolveApiKey();
        if (apiKey == null || keyInvalidForBoot.get()) {
            metrics.recordOutcome(AiMetrics.KIND_SUMMARY, AiMetrics.OUTCOME_FALLBACK);
            metrics.recordFallback(AiMetrics.REASON_NO_KEY);
            return Optional.empty();
        }

        String key = cacheKey(items, differences, language);
        Cache cache = cacheManager.getCache(CACHE_NAME);
        if (cache != null) {
            String cached = cache.get(key, String.class);
            if (cached != null) {
                metrics.recordOutcome(AiMetrics.KIND_SUMMARY, AiMetrics.OUTCOME_CACHE_HIT);
                metrics.recordFallback(AiMetrics.REASON_CACHE_HIT);
                return Optional.of(cached);
            }
        }

        if (!budget.tryConsume()) {
            metrics.recordOutcome(AiMetrics.KIND_SUMMARY, AiMetrics.OUTCOME_FALLBACK);
            metrics.recordFallback(AiMetrics.REASON_BUDGET);
            return Optional.empty();
        }

        ChatModel chatModel = chatModelProvider.getIfAvailable();
        if (chatModel == null) {
            metrics.recordOutcome(AiMetrics.KIND_SUMMARY, AiMetrics.OUTCOME_FALLBACK);
            metrics.recordFallback(AiMetrics.REASON_NO_KEY);
            return Optional.empty();
        }

        String renderedPrompt;
        try {
            renderedPrompt = renderPrompt(items, differences, language);
        } catch (JsonProcessingException ex) {
            log.warn("Failed to serialise compare payload for LLM prompt: {}", ex.getMessage());
            metrics.recordOutcome(AiMetrics.KIND_SUMMARY, AiMetrics.OUTCOME_FALLBACK);
            metrics.recordFallback(AiMetrics.REASON_EXCEPTION);
            return Optional.empty();
        }

        long startedAt = System.nanoTime();
        try {
            ChatResponse response = invokeWithTimeout(chatModel, renderedPrompt);
            String content = extractContent(response);
            recordTokens(response.getMetadata() == null ? null : response.getMetadata().getUsage());
            metrics.recordLatency(AiMetrics.KIND_SUMMARY, Duration.ofNanos(System.nanoTime() - startedAt));
            if (content == null || content.isBlank()) {
                metrics.recordOutcome(AiMetrics.KIND_SUMMARY, AiMetrics.OUTCOME_FALLBACK);
                metrics.recordFallback(AiMetrics.REASON_EXCEPTION);
                return Optional.empty();
            }
            String summary = content.trim();
            if (cache != null) {
                cache.put(key, summary);
            }
            metrics.recordOutcome(AiMetrics.KIND_SUMMARY, AiMetrics.OUTCOME_OK);
            return Optional.of(summary);
        } catch (TimeoutException ex) {
            metrics.recordLatency(AiMetrics.KIND_SUMMARY, Duration.ofNanos(System.nanoTime() - startedAt));
            metrics.recordOutcome(AiMetrics.KIND_SUMMARY, AiMetrics.OUTCOME_TIMEOUT);
            metrics.recordFallback(AiMetrics.REASON_TIMEOUT);
            return Optional.empty();
        } catch (Throwable ex) {
            metrics.recordLatency(AiMetrics.KIND_SUMMARY, Duration.ofNanos(System.nanoTime() - startedAt));
            String reason = classifyError(ex);
            if (AiMetrics.REASON_AUTH.equals(reason)) {
                keyInvalidForBoot.set(true);
            }
            metrics.recordOutcome(AiMetrics.KIND_SUMMARY, AiMetrics.OUTCOME_FALLBACK);
            metrics.recordFallback(reason);
            log.warn("LLM summary fallback (reason={}): {}", reason, ex.getMessage());
            return Optional.empty();
        }
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

    private String renderPrompt(List<CompareItem> items, List<DifferenceEntry> differences, Language language)
            throws JsonProcessingException {
        Map<String, String> bindings = new LinkedHashMap<>();
        bindings.put("language", language.tag());
        bindings.put("products", objectMapper.writeValueAsString(slimItems(items)));
        bindings.put("differences", objectMapper.writeValueAsString(differences));
        return promptLoader.render(PROMPT_TEMPLATE, bindings);
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

    private String cacheKey(List<CompareItem> items, List<DifferenceEntry> differences, Language language) {
        String ids = items.stream()
                .map(CompareItem::id)
                .filter(Objects::nonNull)
                .sorted()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
        return language.tag() + "|" + ids + "|" + differences.hashCode();
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

    private void recordTokens(Usage usage) {
        if (usage == null) {
            return;
        }
        Long prompt = usage.getPromptTokens();
        Long completion = usage.getGenerationTokens();
        if (prompt != null) {
            metrics.recordTokens(AiMetrics.KIND_SUMMARY, AiMetrics.DIRECTION_IN, prompt);
        }
        if (completion != null) {
            metrics.recordTokens(AiMetrics.KIND_SUMMARY, AiMetrics.DIRECTION_OUT, completion);
        }
    }

    private String classifyError(Throwable ex) {
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
}
