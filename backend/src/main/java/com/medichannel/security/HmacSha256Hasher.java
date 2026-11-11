package com.medichannel.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

@Component
public class HmacSha256Hasher {

    private static final String HMAC_SHA256 = "HmacSHA256";

    private final byte[] hmacKey;

    public HmacSha256Hasher(@Value("${medichannel.crypto.hmac-key}") String hmacKeyBase64) {
        byte[] decoded = Base64.getDecoder().decode(hmacKeyBase64);
        if (decoded.length < 32) {
            throw new IllegalStateException("HMAC_SHA256_KEY must be a Base64-encoded key of at least 32 bytes");
        }
        this.hmacKey = decoded;
    }

    public String hashEmail(String email) {
        return hash(normaliseEmail(email));
    }

    public String hashNic(String nic) {
        return hash(normaliseNic(nic));
    }

    public static String normaliseEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    public static String normaliseNic(String nic) {
        return nic == null ? "" : nic.trim().toUpperCase(Locale.ROOT);
    }

    public String hash(String normalisedValue) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(hmacKey, HMAC_SHA256));
            byte[] digest = mac.doFinal(normalisedValue.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Unable to compute HMAC-SHA256", ex);
        }
    }
}
