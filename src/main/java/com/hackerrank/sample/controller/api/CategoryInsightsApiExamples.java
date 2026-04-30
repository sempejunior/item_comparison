package com.hackerrank.sample.controller.api;

final class CategoryInsightsApiExamples {

    private CategoryInsightsApiExamples() {
    }

    static final String INVALID_CATEGORY = """
            {
              "type": "https://api.example.com/errors/bad-request",
              "title": "Malformed parameter",
              "status": 400,
              "detail": "parameter 'category' has invalid value: BOGUS",
              "instance": "/api/v1/products/category-insights"
            }""";

    static final String TOPK_OUT_OF_RANGE = """
            {
              "type": "https://api.example.com/errors/validation",
              "title": "Validation failed",
              "status": 400,
              "detail": "One or more parameters are invalid",
              "instance": "/api/v1/products/category-insights",
              "errors": [{"field": "topK", "message": "must be less than or equal to 20"}]
            }""";

    static final String INVALID_LANGUAGE = """
            {
              "type": "https://api.example.com/errors/bad-request",
              "title": "Invalid language",
              "status": 400,
              "detail": "unsupported language tag: fr",
              "instance": "/api/v1/products/category-insights"
            }""";

    static final String MIN_PRICE_NEGATIVE = """
            {
              "type": "https://api.example.com/errors/validation",
              "title": "Validation failed",
              "status": 400,
              "detail": "Request body is invalid",
              "instance": "/api/v1/products/category-insights",
              "errors": [{"field": "minPrice", "message": "must be greater than or equal to 0"}]
            }""";

    static final String BOUNDS_INCONSISTENT = """
            {
              "type": "https://api.example.com/errors/validation",
              "title": "Validation failed",
              "status": 400,
              "detail": "Request body is invalid",
              "instance": "/api/v1/products/category-insights",
              "errors": [{"field": "minPrice", "message": "minPrice must be less than or equal to maxPrice"}]
            }""";

    static final String MIN_RATING_OUT_OF_RANGE = """
            {
              "type": "https://api.example.com/errors/validation",
              "title": "Validation failed",
              "status": 400,
              "detail": "Request body is invalid",
              "instance": "/api/v1/products/category-insights",
              "errors": [{"field": "minRating", "message": "must be less than or equal to 5"}]
            }""";
}
