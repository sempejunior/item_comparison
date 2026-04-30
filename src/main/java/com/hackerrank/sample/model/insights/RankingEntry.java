package com.hackerrank.sample.model.insights;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RankingEntry(
        String path,
        boolean isComparable,
        Coverage coverage,
        RankedItem winner,
        RankedItem runnerUp,
        Spread spread) {
}
