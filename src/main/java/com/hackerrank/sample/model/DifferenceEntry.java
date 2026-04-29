package com.hackerrank.sample.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record DifferenceEntry(
        String path,
        boolean isComparable,
        Long winnerId,
        Map<Long, Object> values) {
}
