package com.hackerrank.sample.model.insights;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = FilterBoundsConsistentValidator.class)
public @interface FilterBoundsConsistent {

    String message() default "minPrice must be less than or equal to maxPrice";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
