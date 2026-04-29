package com.hackerrank.sample.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CompareItem(
        Long id,
        String name,
        String imageUrl,
        Double rating,
        Category category,
        Map<String, Object> attributes,
        BuyBox buyBox,
        List<ProductDetail.Offer> offers) {
}
