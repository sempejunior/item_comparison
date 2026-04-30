package com.hackerrank.sample.controller;

import com.hackerrank.sample.controller.api.CategoryInsightsApi;
import com.hackerrank.sample.model.Category;
import com.hackerrank.sample.model.Language;
import com.hackerrank.sample.model.insights.CategoryInsightsResponse;
import com.hackerrank.sample.model.insights.InsightsFiltersRequest;
import com.hackerrank.sample.service.insights.CategoryInsightsService;
import com.hackerrank.sample.service.insights.InsightsFilters;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/products")
public class CategoryInsightsController implements CategoryInsightsApi {

    private final CategoryInsightsService insightsService;

    public CategoryInsightsController(CategoryInsightsService insightsService) {
        this.insightsService = insightsService;
    }

    @Override
    public CategoryInsightsResponse categoryInsights(
            Category category, int topK, String language, InsightsFiltersRequest filters) {
        Language parsedLanguage = Language.fromTag(language);
        InsightsFilters parsedFilters = InsightsFilters.from(filters);
        return insightsService.insights(category, topK, parsedLanguage, parsedFilters);
    }
}
