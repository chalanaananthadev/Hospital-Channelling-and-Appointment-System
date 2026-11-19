package com.medichannel.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

public class SlmcNumberValidator implements ConstraintValidator<SlmcNumber, String> {

    private static final Pattern SLMC_PATTERN = Pattern.compile("^(?i)(SLMC[- ]?)?\\d{4,6}$");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value != null && SLMC_PATTERN.matcher(value.trim()).matches();
    }
}