package com.hackerrank.sample.controller.advice;

import com.hackerrank.sample.exception.ProductNotFoundException;
import com.hackerrank.sample.model.problem.ProblemDetail7807;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private final String errorsBaseUri;

    public GlobalExceptionHandler(@Value("${app.errors.base-uri:https://api.example.com/errors}") String errorsBaseUri) {
        this.errorsBaseUri = errorsBaseUri;
    }

    @ExceptionHandler(ProductNotFoundException.class)
    public ResponseEntity<ProblemDetail7807> handleNotFound(
            ProductNotFoundException ex, HttpServletRequest request) {
        ProblemDetail7807 body = new ProblemDetail7807(
                errorsBaseUri + "/not-found",
                "Product not found",
                HttpStatus.NOT_FOUND.value(),
                ex.getMessage(),
                request.getRequestURI(),
                null,
                null);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }
}
