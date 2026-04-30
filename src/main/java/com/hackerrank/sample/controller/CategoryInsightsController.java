package com.hackerrank.sample.controller;

import com.hackerrank.sample.controller.api.CategoryInsightsApi;
import com.hackerrank.sample.model.Category;
import com.hackerrank.sample.model.Language;
import com.hackerrank.sample.model.insights.AppliedFilters;
import com.hackerrank.sample.model.insights.CategoryInsightsResponse;
import com.hackerrank.sample.model.insights.InsightsFiltersRequest;
import com.hackerrank.sample.service.insights.CategoryInsightsService;
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
        CategoryInsightsResponse base = insightsService.insights(category, topK, parsedLanguage);
        AppliedFilters applied = AppliedFilters.from(filters);
        if (applied == null) {
            return base;
        }
        return new CategoryInsightsResponse(
                base.category(),
                base.productCount(),
                base.rankings(),
                base.topItems(),
                base.language(),
                base.summary(),
                applied);
    }
}
