package com.hackerrank.sample.model.insights;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.hackerrank.sample.model.Category;

import java.math.BigDecimal;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TopItem(
        Long id,
        String name,
        Category category,
        String imageUrl,
        BigDecimal price,
        String currency,
        Double rating) {
}
