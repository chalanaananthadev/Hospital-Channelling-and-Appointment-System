package com.medichannel.security;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class HmacSha256HasherTest {

    @Test
    void emailHashIsStableAndCaseInsensitive() {
        byte[] key = new byte[32];
        HmacSha256Hasher hasher = new HmacSha256Hasher(Base64.getEncoder().encodeToString(key));
        assertEquals(hasher.hashEmail("Patient@LankaCare.lk"), hasher.hashEmail("patient@lankacare.lk"));
        assertEquals(64, hasher.hashEmail("patient@lankacare.lk").length());
        assertNotEquals(hasher.hashEmail("a@b.lk"), hasher.hashEmail("c@d.lk"));
    }
}
