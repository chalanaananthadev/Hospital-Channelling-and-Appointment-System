package com.medichannel.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class AesGcmAttributeConverterTest {

    private AesGcmAttributeConverter converter;

    @BeforeEach
    void setUp() {
        byte[] key = new byte[32];
        for (int i = 0; i < key.length; i++) {
            key[i] = (byte) i;
        }
        EncryptionService service = new EncryptionService(Base64.getEncoder().encodeToString(key));
        service.init();
        converter = new AesGcmAttributeConverter();
    }

    @Test
    void encryptsWithFreshIvAndDecrypts() {
        String first = converter.convertToDatabaseColumn("Nimal Perera");
        String second = converter.convertToDatabaseColumn("Nimal Perera");
        assertNotEquals(first, second);
        assertEquals("Nimal Perera", converter.convertToEntityAttribute(first));
        assertEquals("Nimal Perera", converter.convertToEntityAttribute(second));
    }
}
