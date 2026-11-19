package com.medichannel.validation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlmcNumberValidatorTest {

    private final SlmcNumberValidator validator = new SlmcNumberValidator();

    @Test
    void acceptsValidSriLankanSlmcFormats() {
        assertTrue(validator.isValid("SLMC-12345", null));
        assertTrue(validator.isValid("slmc-123456", null));
        assertTrue(validator.isValid("SLMC 1234", null));
        assertTrue(validator.isValid("12345", null));
    }

    @Test
    void rejectsInvalidSlmcFormats() {
        assertFalse(validator.isValid(null, null));
        assertFalse(validator.isValid("", null));
        assertFalse(validator.isValid("123", null));
        assertFalse(validator.isValid("1234567", null));
        assertFalse(validator.isValid("ABC-12345", null));
    }
}