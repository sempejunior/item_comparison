package com.hackerrank.sample.model.insights;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;

/**
 * Echo of the structured filters that were applied to a
 * {@code /category-insights} response (SPEC-005 v5 FR-2 / FR-19).
 *
 * <p>Only included in the response when at least one filter was
 * supplied; absent fields inside the record are themselves omitted via
 * {@link JsonInclude} so callers see the minimal shape.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AppliedFilters(BigDecimal minPrice, BigDecimal maxPrice, Double minRating) {

    public static AppliedFilters from(InsightsFiltersRequest request) {
        if (request == null || request.isEmpty()) {
            return null;
        }
        return new AppliedFilters(request.getMinPrice(), request.getMaxPrice(), request.getMinRating());
    }
}
