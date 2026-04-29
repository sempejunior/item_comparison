package com.hackerrank.sample.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProductSummary(
        Long id,
        String name,
        String imageUrl,
        Double rating,
        Category category) {
}
