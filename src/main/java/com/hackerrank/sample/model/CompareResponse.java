package com.hackerrank.sample.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CompareResponse(
        List<CompareItem> items,
        List<DifferenceEntry> differences,
        String summary) {
}
