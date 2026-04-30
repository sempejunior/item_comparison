package com.hackerrank.sample.controller.advice;

import com.hackerrank.sample.exception.InvalidCompareRequestException;
import com.hackerrank.sample.exception.InvalidFieldsException;
import com.hackerrank.sample.exception.InvalidLanguageException;
import com.hackerrank.sample.exception.ProductNotFoundException;
import com.hackerrank.sample.exception.ProductsNotFoundException;
import com.hackerrank.sample.model.problem.ProblemDetail7807;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.ConversionNotSupportedException;
import org.springframework.core.convert.ConversionFailedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final String errorsBaseUri;

    public GlobalExceptionHandler(@Value("${app.errors.base-uri:https://api.example.com/errors}") String errorsBaseUri) {
        this.errorsBaseUri = errorsBaseUri;
    }

    @ExceptionHandler(ProductNotFoundException.class)
    public ResponseEntity<ProblemDetail7807> handleNotFound(
            ProductNotFoundException ex, HttpServletRequest request) {
        return problem("not-found", "Product not found", HttpStatus.NOT_FOUND,
                ex.getMessage(), request, null, null);
    }

    @ExceptionHandler(ProductsNotFoundException.class)
    public ResponseEntity<ProblemDetail7807> handleProductsNotFound(
            ProductsNotFoundException ex, HttpServletRequest request) {
        return problem("products-not-found", "Product(s) not found", HttpStatus.NOT_FOUND,
                "One or more product ids do not exist", request, null, ex.getMissingIds());
    }

    @ExceptionHandler(InvalidFieldsException.class)
    public ResponseEntity<ProblemDetail7807> handleInvalidFields(
            InvalidFieldsException ex, HttpServletRequest request) {
        return problem("bad-request", "Invalid fields parameter", HttpStatus.BAD_REQUEST,
                ex.getMessage(), request, null, null);
    }

    @ExceptionHandler(InvalidCompareRequestException.class)
    public ResponseEntity<ProblemDetail7807> handleInvalidCompare(
            InvalidCompareRequestException ex, HttpServletRequest request) {
        return problem("validation", "Invalid compare request", HttpStatus.BAD_REQUEST,
                ex.getMessage(), request, null, null);
    }

    @ExceptionHandler(InvalidLanguageException.class)
    public ResponseEntity<ProblemDetail7807> handleInvalidLanguage(
            InvalidLanguageException ex, HttpServletRequest request) {
        return problem("bad-request", "Invalid language", HttpStatus.BAD_REQUEST,
                ex.getMessage(), request, null, null);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail7807> handleConstraint(
            ConstraintViolationException ex, HttpServletRequest request) {
        List<ProblemDetail7807.FieldError> errors = ex.getConstraintViolations().stream()
                .map(GlobalExceptionHandler::toFieldError)
                .toList();
        return problem("validation", "Validation failed", HttpStatus.BAD_REQUEST,
                "One or more parameters are invalid", request, errors, null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail7807> handleMethodArg(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<ProblemDetail7807.FieldError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(GlobalExceptionHandler::toFieldError)
                .toList();
        return problem("validation", "Validation failed", HttpStatus.BAD_REQUEST,
                "Request body is invalid", request, errors, null);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail7807> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        String detail = "parameter '" + ex.getName() + "' has invalid value: " + ex.getValue();
        return problem("bad-request", "Malformed parameter", HttpStatus.BAD_REQUEST,
                detail, request, null, null);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ProblemDetail7807> handleMissingParam(
            MissingServletRequestParameterException ex, HttpServletRequest request) {
        List<ProblemDetail7807.FieldError> errors = List.of(
                new ProblemDetail7807.FieldError(ex.getParameterName(), "is required"));
        return problem("validation", "Validation failed", HttpStatus.BAD_REQUEST,
                "Missing required parameter: " + ex.getParameterName(), request, errors, null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ProblemDetail7807> handleNoResource(
            NoResourceFoundException ex, HttpServletRequest request) {
        return problem("not-found", "Resource not found", HttpStatus.NOT_FOUND,
                "no handler for " + request.getMethod() + " " + request.getRequestURI(),
                request, null, null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ProblemDetail7807> handleMethodNotAllowed(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        return problem("method-not-allowed", "Method not allowed", HttpStatus.METHOD_NOT_ALLOWED,
                "method " + ex.getMethod() + " is not supported on this resource",
                request, null, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail7807> handleFallback(
            Exception ex, HttpServletRequest request) {
        if (isParameterConversionFailure(ex)) {
            String parameter = guessParameterName(request);
            String detail = "parameter '" + parameter + "' has invalid value";
            return problem("bad-request", "Malformed parameter", HttpStatus.BAD_REQUEST,
                    detail, request, null, null);
        }
        LOG.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return problem("internal", "Internal server error", HttpStatus.INTERNAL_SERVER_ERROR,
                "Unexpected error", request, null, null);
    }

    private static boolean isParameterConversionFailure(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof ConversionFailedException
                    || current instanceof ConversionNotSupportedException
                    || current instanceof NumberFormatException) {
                return true;
            }
            if (current.getCause() == current) {
                break;
            }
            current = current.getCause();
        }
        return false;
    }

    private static String guessParameterName(HttpServletRequest request) {
        String query = request.getQueryString();
        if (query == null || query.isBlank()) {
            return "request";
        }
        int eq = query.indexOf('=');
        return eq > 0 ? query.substring(0, eq) : "request";
    }

    private ResponseEntity<ProblemDetail7807> problem(
            String slug, String title, HttpStatus status, String detail,
            HttpServletRequest request,
            List<ProblemDetail7807.FieldError> errors,
            List<Long> missingIds) {
        ProblemDetail7807 body = new ProblemDetail7807(
                errorsBaseUri + "/" + slug,
                title,
                status.value(),
                detail,
                request.getRequestURI(),
                errors,
                missingIds);
        return ResponseEntity.status(status).body(body);
    }

    private static ProblemDetail7807.FieldError toFieldError(ConstraintViolation<?> v) {
        return new ProblemDetail7807.FieldError(stripMethodPrefix(v.getPropertyPath().toString()), v.getMessage());
    }

    private static String stripMethodPrefix(String propertyPath) {
        int lastDot = propertyPath.lastIndexOf('.');
        return lastDot >= 0 ? propertyPath.substring(lastDot + 1) : propertyPath;
    }

    private static ProblemDetail7807.FieldError toFieldError(FieldError v) {
        return new ProblemDetail7807.FieldError(v.getField(), v.getDefaultMessage());
    }
}
