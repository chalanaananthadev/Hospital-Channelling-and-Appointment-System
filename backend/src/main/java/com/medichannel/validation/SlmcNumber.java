package com.medichannel.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Constraint(validatedBy = SlmcNumberValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface SlmcNumber {
    String message() default "SLMC number must be 4 to 6 digits (optionally prefixed with SLMC-, e.g., SLMC-12345)";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}