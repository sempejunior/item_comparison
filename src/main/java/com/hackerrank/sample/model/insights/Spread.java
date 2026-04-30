package com.hackerrank.sample.model.insights;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record Spread(Object min, Object max, Object median) {
}
