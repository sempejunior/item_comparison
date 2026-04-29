package com.hackerrank.sample.controller.advice;

import com.hackerrank.sample.model.problem.ProblemDetail7807;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler =
            new GlobalExceptionHandler("https://api.example.com/errors");

    @Test
    void constraintViolation_stripsMethodPrefixFromFieldPath() {
        ConstraintViolation<?> violation = mock(ConstraintViolation.class);
        Path path = mock(Path.class);
        when(path.toString()).thenReturn("compare.ids");
        when(violation.getPropertyPath()).thenReturn(path);
        when(violation.getMessage()).thenReturn("must not be empty");
        ConstraintViolationException ex = new ConstraintViolationException(Set.of(violation));

        HttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/products/compare");
        ResponseEntity<ProblemDetail7807> response = handler.handleConstraint(ex, req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ProblemDetail7807 body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.errors()).hasSize(1);
        ProblemDetail7807.FieldError error = body.errors().get(0);
        assertThat(error.field()).isEqualTo("ids");
        assertThat(error.message()).isEqualTo("must not be empty");
    }

    @Test
    void constraintViolation_keepsFieldUnchangedWhenNoPrefix() {
        ConstraintViolation<?> violation = mock(ConstraintViolation.class);
        Path path = mock(Path.class);
        when(path.toString()).thenReturn("ids");
        when(violation.getPropertyPath()).thenReturn(path);
        when(violation.getMessage()).thenReturn("invalid");
        ConstraintViolationException ex = new ConstraintViolationException(Set.of(violation));

        HttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/products/compare");
        List<ProblemDetail7807.FieldError> errors = handler.handleConstraint(ex, req).getBody().errors();

        assertThat(errors).singleElement()
                .satisfies(e -> assertThat(e.field()).isEqualTo("ids"));
    }
}
