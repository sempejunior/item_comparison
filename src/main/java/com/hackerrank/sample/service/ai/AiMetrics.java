package com.hackerrank.sample.service.ai;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Centralised Micrometer meters for AI surfaces.
 *
 * <p>All call sites go through these helpers so tag keys and values stay
 * consistent across the codebase. Meters are registered lazily by Micrometer
 * the first time we touch them; counters and timers exposed here resolve
 * once and are reused.</p>
 */
@Component
public class AiMetrics {

    public static final String CALLS_TOTAL = "ai_calls_total";
    public static final String LATENCY_SECONDS = "ai_latency_seconds";
    public static final String TOKENS_TOTAL = "ai_tokens_total";
    public static final String FALLBACK_TOTAL = "ai_fallback_total";

    public static final String TAG_KIND = "kind";
    public static final String TAG_OUTCOME = "outcome";
    public static final String TAG_DIRECTION = "direction";
    public static final String TAG_REASON = "reason";

    public static final String KIND_SUMMARY = "summary";

    public static final String OUTCOME_OK = "ok";
    public static final String OUTCOME_CACHE_HIT = "cache_hit";
    public static final String OUTCOME_TIMEOUT = "timeout";
    public static final String OUTCOME_ERROR = "error";
    public static final String OUTCOME_FALLBACK = "fallback";

    public static final String DIRECTION_IN = "in";
    public static final String DIRECTION_OUT = "out";

    public static final String REASON_NO_KEY = "no_key";
    public static final String REASON_BUDGET = "budget";
    public static final String REASON_TIMEOUT = "timeout";
    public static final String REASON_SERVER_ERROR = "server_error";
    public static final String REASON_EXCEPTION = "exception";
    public static final String REASON_CACHE_HIT = "cache_hit";

    private final MeterRegistry registry;

    public AiMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void recordOutcome(String kind, String outcome) {
        Counter.builder(CALLS_TOTAL)
                .tag(TAG_KIND, kind)
                .tag(TAG_OUTCOME, outcome)
                .register(registry)
                .increment();
    }

    public void recordLatency(String kind, Duration elapsed) {
        Timer.builder(LATENCY_SECONDS)
                .tag(TAG_KIND, kind)
                .register(registry)
                .record(elapsed);
    }

    public void recordTokens(String kind, String direction, long tokens) {
        if (tokens <= 0) {
            return;
        }
        Counter.builder(TOKENS_TOTAL)
                .tag(TAG_KIND, kind)
                .tag(TAG_DIRECTION, direction)
                .register(registry)
                .increment(tokens);
    }

    public void recordFallback(String reason) {
        Counter.builder(FALLBACK_TOTAL)
                .tag(TAG_REASON, reason)
                .register(registry)
                .increment();
    }
}
