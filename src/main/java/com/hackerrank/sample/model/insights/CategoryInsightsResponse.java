package com.hackerrank.sample.model.insights;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.hackerrank.sample.model.Category;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CategoryInsightsResponse(
        Category category,
        int productCount,
        List<RankingEntry> rankings,
        List<TopItem> topItems,
        String language,
        String summary,
        AppliedFilters appliedFilters) {
}
