package com.medichannel.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Constraint(validatedBy = SriLankanNicValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface SriLankanNic {
    String message() default "NIC must be 9 digits followed by V or X, or 12 digits";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
