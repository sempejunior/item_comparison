package com.hackerrank.sample.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CompareResponse(
        List<String> fields,
        String language,
        boolean crossCategory,
        List<CompareItem> items,
        List<DifferenceEntry> differences,
        Map<Long, List<String>> exclusiveAttributes,
        String summary) {
}
