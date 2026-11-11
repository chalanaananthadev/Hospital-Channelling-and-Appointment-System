package com.medichannel.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

public class SriLankanNicValidator implements ConstraintValidator<SriLankanNic, String> {

    private static final Pattern NIC = Pattern.compile("^(\\d{9}[VXvx]|\\d{12})$");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value != null && NIC.matcher(value.trim()).matches();
    }
}
