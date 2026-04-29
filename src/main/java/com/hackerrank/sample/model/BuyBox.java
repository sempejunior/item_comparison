package com.hackerrank.sample.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record BuyBox(
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
