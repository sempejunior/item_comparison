package com.hackerrank.sample.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProductDetail(
        Long id,
        String name,
        String description,
        String imageUrl,
        Double rating,
        Category category,
        Map<String, Object> attributes,
        List<Offer> offers,
        BuyBox buyBox) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Offer(
            Long id,
            String sellerId,
            String sellerName,
            Integer sellerReputation,
            BigDecimal price,
            String currency,
            Condition condition,
            Boolean freeShipping,
            Integer stock) {
    }
}
