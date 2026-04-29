package com.hackerrank.sample.service.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-process daily request guard for AI calls.
 *
 * <p>Counts non-cached calls and rejects further consumption once the
 * configured ceiling is reached. The counter resets when the local-zone
 * date rolls over and on JVM restart, which is acceptable for the
 * single-instance, in-memory deliverable described in SPEC-004 §8.</p>
 *
 * <p>A limit of {@code 0} disables the guard entirely (default).</p>
 */
@Component
public class DailyBudget {

    private final long limit;
    private final Clock clock;
    private final AtomicLong currentDay;
    private final AtomicLong consumed = new AtomicLong(0);

    public DailyBudget(@Value("${app.ai.daily-request-limit:0}") long limit) {
        this(limit, Clock.systemDefaultZone());
    }

    DailyBudget(long limit, Clock clock) {
        this.limit = limit;
        this.clock = clock;
        this.currentDay = new AtomicLong(today());
    }

    public synchronized boolean tryConsume() {
        if (limit <= 0) {
            return true;
        }
        long today = today();
        if (currentDay.get() != today) {
            currentDay.set(today);
            consumed.set(0);
        }
        if (consumed.get() >= limit) {
            return false;
        }
        consumed.incrementAndGet();
        return true;
    }

    public long remaining() {
        if (limit <= 0) {
            return Long.MAX_VALUE;
        }
        return Math.max(0, limit - consumed.get());
    }

    private long today() {
        return LocalDate.now(clock).toEpochDay();
    }
}
