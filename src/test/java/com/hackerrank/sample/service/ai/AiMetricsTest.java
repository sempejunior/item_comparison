package com.hackerrank.sample.service.ai;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class AiMetricsTest {

    private MeterRegistry registry;
    private AiMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new AiMetrics(registry);
    }

    @Test
    void recordsOutcomeWithKindAndOutcomeTags() {
        metrics.recordOutcome(AiMetrics.KIND_SUMMARY, AiMetrics.OUTCOME_OK);
        metrics.recordOutcome(AiMetrics.KIND_SUMMARY, AiMetrics.OUTCOME_OK);
        metrics.recordOutcome(AiMetrics.KIND_SUMMARY, AiMetrics.OUTCOME_FALLBACK);

        Counter ok = registry.get(AiMetrics.CALLS_TOTAL)
                .tag(AiMetrics.TAG_KIND, AiMetrics.KIND_SUMMARY)
                .tag(AiMetrics.TAG_OUTCOME, AiMetrics.OUTCOME_OK)
                .counter();
        Counter fallback = registry.get(AiMetrics.CALLS_TOTAL)
                .tag(AiMetrics.TAG_OUTCOME, AiMetrics.OUTCOME_FALLBACK)
                .counter();

        assertThat(ok.count()).isEqualTo(2.0);
        assertThat(fallback.count()).isEqualTo(1.0);
    }

    @Test
    void recordsLatencyTimerKeyedByKind() {
        metrics.recordLatency(AiMetrics.KIND_SUMMARY, Duration.ofMillis(150));

        Timer timer = registry.get(AiMetrics.LATENCY_SECONDS)
                .tag(AiMetrics.TAG_KIND, AiMetrics.KIND_SUMMARY)
                .timer();
        assertThat(timer.count()).isEqualTo(1L);
        assertThat(timer.totalTime(TimeUnit.MILLISECONDS)).isCloseTo(150.0, Offset.offset(5.0));
    }

    @Test
    void tokensCounterIncrementsByAmount() {
        metrics.recordTokens(AiMetrics.KIND_SUMMARY, AiMetrics.DIRECTION_IN, 320);
        metrics.recordTokens(AiMetrics.KIND_SUMMARY, AiMetrics.DIRECTION_OUT, 84);

        Counter in = registry.get(AiMetrics.TOKENS_TOTAL)
                .tag(AiMetrics.TAG_DIRECTION, AiMetrics.DIRECTION_IN)
                .counter();
        Counter out = registry.get(AiMetrics.TOKENS_TOTAL)
                .tag(AiMetrics.TAG_DIRECTION, AiMetrics.DIRECTION_OUT)
                .counter();

        assertThat(in.count()).isEqualTo(320.0);
        assertThat(out.count()).isEqualTo(84.0);
    }

    @Test
    void tokensIgnoredWhenNonPositive() {
        metrics.recordTokens(AiMetrics.KIND_SUMMARY, AiMetrics.DIRECTION_IN, 0);
        metrics.recordTokens(AiMetrics.KIND_SUMMARY, AiMetrics.DIRECTION_IN, -5);

        assertThat(registry.find(AiMetrics.TOKENS_TOTAL).counter()).isNull();
    }

    @Test
    void fallbackReasonsAccumulateIndependently() {
        metrics.recordFallback(AiMetrics.REASON_NO_KEY);
        metrics.recordFallback(AiMetrics.REASON_TIMEOUT);
        metrics.recordFallback(AiMetrics.REASON_TIMEOUT);

        Counter noKey = registry.get(AiMetrics.FALLBACK_TOTAL)
                .tag(AiMetrics.TAG_REASON, AiMetrics.REASON_NO_KEY)
                .counter();
        Counter timeout = registry.get(AiMetrics.FALLBACK_TOTAL)
                .tag(AiMetrics.TAG_REASON, AiMetrics.REASON_TIMEOUT)
                .counter();

        assertThat(noKey.count()).isEqualTo(1.0);
        assertThat(timeout.count()).isEqualTo(2.0);
    }
}
