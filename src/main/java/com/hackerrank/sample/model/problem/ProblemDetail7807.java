package com.hackerrank.sample.model.problem;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProblemDetail7807(
        String type,
        String title,
        int status,
        String detail,
        String instance,
        List<FieldError> errors,
        List<Long> missingIds) {

    public record FieldError(String field, String message) {
    }
}
