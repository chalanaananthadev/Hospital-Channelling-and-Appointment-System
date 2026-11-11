package com.medichannel.security;

import com.medichannel.entity.UserAccount;
import com.medichannel.enums.Role;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class JwtServiceTest {

    private JwtService jwtService;
    private static final String TEST_SECRET = "superSecretKeyForJwtTokensThatIsAtLeast32BytesLong!";
    private static final long EXPIRATION_MS = 3600000; // 1 hour

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(TEST_SECRET, EXPIRATION_MS);
    }

    @Test
    @DisplayName("Issues signed JWT with user ID and role, parses correctly")
    void testIssueAndParseToken() {
        UUID userId = UUID.randomUUID();
        UserAccount user = new UserAccount();
        user.setId(userId);
        user.setRole(Role.DOCTOR);

        String token = jwtService.issueToken(user);
        assertNotNull(token);

        Claims claims = jwtService.parse(token);
        assertEquals(userId.toString(), claims.getSubject());
        assertEquals("DOCTOR", claims.get("role"));
        assertEquals(userId, jwtService.extractUserId(token));
    }
}

