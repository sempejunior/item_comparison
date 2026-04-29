package com.hackerrank.sample.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hackerrank.sample.model.BuyBox;
import com.hackerrank.sample.model.Category;
import com.hackerrank.sample.model.CompareItem;
import com.hackerrank.sample.model.Condition;
import com.hackerrank.sample.model.DifferenceEntry;
import com.hackerrank.sample.model.Language;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SummaryServiceTest {

    private MeterRegistry registry;
    private AiMetrics metrics;
    private DailyBudget budget;
    private ConcurrentMapCacheManager cacheManager;
    private ChatModel chatModel;
    private ObjectProvider<ChatModel> chatModelProvider;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new AiMetrics(registry);
        budget = new DailyBudget(0);
        cacheManager = new ConcurrentMapCacheManager("ai-summary");
        chatModel = mock(ChatModel.class);
        chatModelProvider = providerOf(chatModel);
    }

    @Test
    void happyPathReturnsSummaryAndRecordsTokens() {
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse("Diferenças resumidas.", 320L, 84L));
        SummaryService service = service(() -> "sk-real");

        Optional<String> result = service.summarise(items(), differences(), Language.PT_BR);

        assertThat(result).contains("Diferenças resumidas.");
        assertThat(counterCount("ai_calls_total", "outcome", "ok")).isEqualTo(1.0);
        assertThat(counterCount("ai_tokens_total", "direction", "in")).isEqualTo(320.0);
        assertThat(counterCount("ai_tokens_total", "direction", "out")).isEqualTo(84.0);
    }

    @Test
    void noKeySkipsModelAndIncrementsFallback() {
        SummaryService service = service(() -> null);

        Optional<String> result = service.summarise(items(), differences(), Language.PT_BR);

        assertThat(result).isEmpty();
        verify(chatModel, never()).call(any(Prompt.class));
        assertThat(counterCount("ai_fallback_total", "reason", "no_key")).isEqualTo(1.0);
    }

    @Test
    void disabledKeyTreatedAsAbsent() {
        SummaryService service = service(() -> "disabled");

        assertThat(service.summarise(items(), differences(), Language.PT_BR)).isEmpty();
        assertThat(counterCount("ai_fallback_total", "reason", "no_key")).isEqualTo(1.0);
        verify(chatModel, never()).call(any(Prompt.class));
    }

    @Test
    void timeoutFallsBackWithTimeoutReason() {
        when(chatModel.call(any(Prompt.class))).thenAnswer(inv -> {
            Thread.sleep(200);
            return chatResponse("late", 0L, 0L);
        });
        SummaryService service = service(() -> "sk-real", 50L);

        Optional<String> result = service.summarise(items(), differences(), Language.PT_BR);

        assertThat(result).isEmpty();
        assertThat(counterCount("ai_calls_total", "outcome", "timeout")).isEqualTo(1.0);
        assertThat(counterCount("ai_fallback_total", "reason", "timeout")).isEqualTo(1.0);
    }

    @Test
    void unauthorizedFlipsKeyInvalidForBoot() {
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("HTTP 401 Unauthorized"));
        SummaryService service = service(() -> "sk-real");

        assertThat(service.summarise(items(), differences(), Language.PT_BR)).isEmpty();
        assertThat(counterCount("ai_fallback_total", "reason", "auth")).isEqualTo(1.0);
        assertThat(service.isKeyInvalidForBoot()).isTrue();

        Optional<String> second = service.summarise(items(), differences(), Language.PT_BR);
        assertThat(second).isEmpty();
        assertThat(counterCount("ai_fallback_total", "reason", "no_key")).isEqualTo(1.0);
        verify(chatModel, times(1)).call(any(Prompt.class));
    }

    @Test
    void serverErrorMapsToServerErrorReason() {
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("HTTP 503 server error"));
        SummaryService service = service(() -> "sk-real");

        assertThat(service.summarise(items(), differences(), Language.PT_BR)).isEmpty();
        assertThat(counterCount("ai_fallback_total", "reason", "server_error")).isEqualTo(1.0);
    }

    @Test
    void clientErrorMapsToClientErrorReason() {
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("HTTP 429 too many requests"));
        SummaryService service = service(() -> "sk-real");

        assertThat(service.summarise(items(), differences(), Language.PT_BR)).isEmpty();
        assertThat(counterCount("ai_fallback_total", "reason", "client_error")).isEqualTo(1.0);
    }

    @Test
    void unexpectedExceptionMapsToExceptionReason() {
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("connection reset"));
        SummaryService service = service(() -> "sk-real");

        assertThat(service.summarise(items(), differences(), Language.PT_BR)).isEmpty();
        assertThat(counterCount("ai_fallback_total", "reason", "exception")).isEqualTo(1.0);
    }

    @Test
    void cacheHitOnSecondCallSkipsModel() {
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse("primeira chamada", 10L, 5L));
        SummaryService service = service(() -> "sk-real");

        Optional<String> first = service.summarise(items(), differences(), Language.PT_BR);
        Optional<String> second = service.summarise(items(), differences(), Language.PT_BR);

        assertThat(first).contains("primeira chamada");
        assertThat(second).contains("primeira chamada");
        verify(chatModel, times(1)).call(any(Prompt.class));
        assertThat(counterCount("ai_calls_total", "outcome", "cache_hit")).isEqualTo(1.0);
        assertThat(counterCount("ai_calls_total", "outcome", "ok")).isEqualTo(1.0);
        assertThat(counterCount("ai_fallback_total", "reason", "cache_hit")).isEqualTo(0.0);
    }

    @Test
    void languageBindingChangesPromptAndCacheKey() {
        AtomicInteger calls = new AtomicInteger();
        when(chatModel.call(any(Prompt.class))).thenAnswer(inv -> {
            Prompt prompt = inv.getArgument(0);
            String text = prompt.getInstructions().get(0).getContent();
            assertThat(text).contains("Language for the response: " + (calls.get() == 0 ? "pt-BR" : "en"));
            calls.incrementAndGet();
            return chatResponse("resposta", 0L, 0L);
        });
        SummaryService service = service(() -> "sk-real");

        service.summarise(items(), differences(), Language.PT_BR);
        service.summarise(items(), differences(), Language.EN);

        verify(chatModel, times(2)).call(any(Prompt.class));
    }

    @Test
    void budgetExhaustedShortCircuitsBeforeCallingModel() {
        DailyBudget exhausted = new DailyBudget(1);
        exhausted.tryConsume();
        SummaryService service = new SummaryService(
                new PromptLoader(), metrics, exhausted, cacheManager,
                chatModelProvider, objectMapper, 2000L, () -> "sk-real");

        Optional<String> result = service.summarise(items(), differences(), Language.PT_BR);

        assertThat(result).isEmpty();
        assertThat(counterCount("ai_fallback_total", "reason", "budget")).isEqualTo(1.0);
        verify(chatModel, never()).call(any(Prompt.class));
    }

    private SummaryService service(Supplier<String> apiKeyResolver) {
        return service(apiKeyResolver, 2000L);
    }

    private SummaryService service(Supplier<String> apiKeyResolver, long timeoutMs) {
        return new SummaryService(
                new PromptLoader(), metrics, budget, cacheManager,
                chatModelProvider, objectMapper, timeoutMs, apiKeyResolver);
    }

    private double counterCount(String name, String tagKey, String tagValue) {
        Counter counter = registry.find(name).tag(tagKey, tagValue).counter();
        return counter == null ? 0.0 : counter.count();
    }

    private static ChatResponse chatResponse(String content, long promptTokens, long completionTokens) {
        Generation generation = new Generation(new AssistantMessage(content));
        ChatResponseMetadata metadata = ChatResponseMetadata.builder()
                .withUsage(new FixedUsage(promptTokens, completionTokens))
                .build();
        return new ChatResponse(List.of(generation), metadata);
    }

    private static List<CompareItem> items() {
        Map<String, Object> a1 = new LinkedHashMap<>();
        a1.put("battery_mah", 4500);
        a1.put("weight_g", 190);
        Map<String, Object> a2 = new LinkedHashMap<>();
        a2.put("battery_mah", 5151);
        a2.put("weight_g", 194);
        BuyBox b1 = new BuyBox(101L, "s1", "Phone Co", 95, new BigDecimal("1999.00"), "BRL", Condition.NEW, true, 10);
        BuyBox b2 = new BuyBox(202L, "s2", "Phone Co", 90, new BigDecimal("2199.00"), "BRL", Condition.NEW, true, 5);
        return List.of(
                new CompareItem(1L, "Phone A", "desc A", null, 4.5, Category.SMARTPHONE, a1, b1, null),
                new CompareItem(2L, "Phone B", "desc B", null, 4.4, Category.SMARTPHONE, a2, b2, null)
        );
    }

    private static List<DifferenceEntry> differences() {
        return List.of(
                new DifferenceEntry("attributes.battery_mah", true, 2L, Map.of(1L, 4500, 2L, 5151)),
                new DifferenceEntry("attributes.weight_g", true, 1L, Map.of(1L, 190, 2L, 194))
        );
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<ChatModel> providerOf(ChatModel chatModel) {
        ObjectProvider<ChatModel> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(chatModel);
        return provider;
    }

    private record FixedUsage(Long promptTokens, Long generationTokens) implements Usage {
        @Override
        public Long getPromptTokens() {
            return promptTokens;
        }

        @Override
        public Long getGenerationTokens() {
            return generationTokens;
        }
    }
}
