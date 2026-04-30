package com.hackerrank.sample.model.insights;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.math.BigDecimal;

public class FilterBoundsConsistentValidator
        implements ConstraintValidator<FilterBoundsConsistent, InsightsFiltersRequest> {

    @Override
    public boolean isValid(InsightsFiltersRequest request, ConstraintValidatorContext context) {
        if (request == null) {
            return true;
        }
        BigDecimal min = request.getMinPrice();
        BigDecimal max = request.getMaxPrice();
        if (min == null || max == null) {
            return true;
        }
        if (min.compareTo(max) <= 0) {
            return true;
        }
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(
                        "minPrice must be less than or equal to maxPrice")
                .addPropertyNode("minPrice")
                .addConstraintViolation();
        return false;
    }
}
