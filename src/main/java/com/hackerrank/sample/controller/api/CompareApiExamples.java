package com.hackerrank.sample.controller.api;

final class CompareApiExamples {

    private CompareApiExamples() {
    }

    static final String DUPLICATE_IDS = """
            {
              "type": "https://api.example.com/errors/validation",
              "title": "Invalid compare request",
              "status": 400,
              "detail": "ids must not contain duplicate values",
              "instance": "/api/v1/products/compare"
            }""";

    static final String NON_POSITIVE_ID = """
            {
              "type": "https://api.example.com/errors/validation",
              "title": "Invalid compare request",
              "status": 400,
              "detail": "ids must be positive",
              "instance": "/api/v1/products/compare"
            }""";

    static final String TRAILING_COMMA = """
            {
              "type": "https://api.example.com/errors/validation",
              "title": "Invalid compare request",
              "status": 400,
              "detail": "ids must not contain blank or null values",
              "instance": "/api/v1/products/compare"
            }""";

    static final String MISSING_IDS = """
            {
              "type": "https://api.example.com/errors/products-not-found",
              "title": "Product(s) not found",
              "status": 404,
              "detail": "One or more product ids do not exist",
              "instance": "/api/v1/products/compare",
              "missingIds": [9999]
            }""";
}
