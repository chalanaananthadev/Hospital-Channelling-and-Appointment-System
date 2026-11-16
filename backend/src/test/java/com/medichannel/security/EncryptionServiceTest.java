package com.medichannel.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EncryptionServiceTest {

    @Test
    @DisplayName("Startup fails fast if AES_256_KEY is null or blank")
    void shouldFailFastWhenKeyIsMissingOrBlank() {
        IllegalStateException nullEx = assertThrows(IllegalStateException.class, () -> new EncryptionService(null));
        assertTrue(nullEx.getMessage().contains("AES_256_KEY must be provided"));

        IllegalStateException blankEx = assertThrows(IllegalStateException.class, () -> new EncryptionService("   "));
        assertTrue(blankEx.getMessage().contains("AES_256_KEY must be provided"));
    }

    @Test
    @DisplayName("Startup fails fast if AES_256_KEY is not 32 bytes")
    void shouldFailFastWhenKeyLengthIsNot32Bytes() {
        String shortKey = Base64.getEncoder().encodeToString("too-short".getBytes());
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> new EncryptionService(shortKey));
        assertTrue(ex.getMessage().contains("32-byte key"));
    }

    @Test
    @DisplayName("EncryptionService initializes successfully with valid 32-byte key")
    void shouldInitializeWithValid32ByteKey() {
        byte[] rawKey = new byte[32];
        for (int i = 0; i < 32; i++) {
            rawKey[i] = (byte) (i + 1);
        }
        String validKey = Base64.getEncoder().encodeToString(rawKey);
        assertDoesNotThrow(() -> {
            EncryptionService service = new EncryptionService(validKey);
            assertNotNull(service);
        });
    }
}
