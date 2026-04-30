package com.hackerrank.sample.controller.api;

final class ProductApiExamples {

    private ProductApiExamples() {
    }

    static final String INVALID_CATEGORY = """
            {
              "type": "https://api.example.com/errors/bad-request",
              "title": "Malformed parameter",
              "status": 400,
              "detail": "parameter 'category' has invalid value: BOGUS",
              "instance": "/api/v1/products"
            }""";

    static final String INVALID_FIELDS = """
            {
              "type": "https://api.example.com/errors/bad-request",
              "title": "Invalid fields parameter",
              "status": 400,
              "detail": "path does not accept sub-paths: name.bogus",
              "instance": "/api/v1/products/1"
            }""";

    static final String NOT_FOUND = """
            {
              "type": "https://api.example.com/errors/not-found",
              "title": "Product not found",
              "status": 404,
              "detail": "product not found: 9999",
              "instance": "/api/v1/products/9999"
            }""";
}
