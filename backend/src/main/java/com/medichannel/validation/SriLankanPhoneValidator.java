package com.medichannel.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

public class SriLankanPhoneValidator implements ConstraintValidator<SriLankanPhone, String> {

    private static final Pattern PHONE = Pattern.compile("^07\\d{8}$");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value != null && PHONE.matcher(value.trim()).matches();
    }
}
