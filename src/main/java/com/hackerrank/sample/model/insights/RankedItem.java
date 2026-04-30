package com.hackerrank.sample.model.insights;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RankedItem(Long id, Object value, String name) {
}
