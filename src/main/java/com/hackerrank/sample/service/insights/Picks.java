package com.hackerrank.sample.service.insights;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.util.List;

/**
 * Pre-computed buying-guide picks fed to the {@code category-insights}
 * LLM prompt. Not exposed on the public API; the LLM only verbalises
 * these deterministic choices, so it cannot invent a recommendation.
 *
 * <p>Any field may be {@code null} when the underlying data is missing
 * (e.g. no product in the category has both rating and price). The
 * prompt template tolerates {@code null} entries by simply omitting
 * the corresponding pick.</p>
 *
 * @param bestOverall highest rating; ties broken by lower price then id
 * @param bestValue   highest {@code rating / price}; ties by higher rating then id
 * @param cheapest    lowest price; ties by higher rating then id
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Picks(Pick bestOverall, Pick bestValue, Pick cheapest) {

    /**
     * @param highlights human-readable strings describing each ranking
     *                   path on which this pick is the deterministic
     *                   winner (e.g. {@code "memory: 26 GB"}). Empty
     *                   when the pick wins no comparable ranking.
     *                   Ground truth for the prompt — the LLM is only
     *                   allowed to cite specs that appear here.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Pick(
            Long id,
            String name,
            BigDecimal price,
            String currency,
            Double rating,
            String reason,
            List<String> highlights) {
    }
}
