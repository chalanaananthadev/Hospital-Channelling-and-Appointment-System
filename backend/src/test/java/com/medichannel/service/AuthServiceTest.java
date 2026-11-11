package com.medichannel.service;

import com.medichannel.dto.auth.AuthResponse;
import com.medichannel.dto.auth.LoginRequest;
import com.medichannel.entity.UserAccount;
import com.medichannel.enums.Role;
import com.medichannel.exception.ApiException;
import com.medichannel.repository.PatientRepository;
import com.medichannel.repository.UserAccountRepository;
import com.medichannel.security.HmacSha256Hasher;
import com.medichannel.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private PatientRepository patientRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private HmacSha256Hasher hmacSha256Hasher;

    @Mock
    private JwtService jwtService;

    @Mock
    private AuditService auditService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userAccountRepository,
                patientRepository,
                passwordEncoder,
                hmacSha256Hasher,
                jwtService,
                auditService,
                5,   // 5 max failed attempts
                15   // 15 minutes lockout
        );
    }

    @Test
    @DisplayName("Successful login resets failed attempts and issues JWT")
    void testSuccessfulLogin() {
        String email = "nimal@lankacare.lk";
        String rawPassword = "ValidPassword123#";
        String emailHmac = "mock-hmac-1234567890";
        UUID userId = UUID.randomUUID();

        UserAccount user = new UserAccount();
        user.setId(userId);
        user.setEmail(email);
        user.setEmailHmac(emailHmac);
        user.setPasswordHash("$2a$10$hashedPasswordHere");
        user.setRole(Role.CUSTOMER);
        user.setFullName("Nimal Silva");
        user.setActive(true);
        user.setFailedLoginAttempts(3);

        when(hmacSha256Hasher.hashEmail(email)).thenReturn(emailHmac);
        when(userAccountRepository.findByEmailHmac(emailHmac)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(rawPassword, user.getPasswordHash())).thenReturn(true);
        when(jwtService.issueToken(user)).thenReturn("mock.jwt.token");

        AuthResponse response = authService.login(new LoginRequest(email, rawPassword), "127.0.0.1");

        assertNotNull(response);
        assertEquals("mock.jwt.token", response.accessToken());
        assertEquals(0, user.getFailedLoginAttempts());
        assertNotNull(user.getLastLoginAt());
        verify(userAccountRepository).save(user);
    }

    @Test
    @DisplayName("Failed login increments attempts and throws generic 401 error")
    void testFailedPasswordIncrementsAttempts() {
        String email = "nimal@lankacare.lk";
        String emailHmac = "mock-hmac-1234567890";

        UserAccount user = new UserAccount();
        user.setId(UUID.randomUUID());
        user.setEmail(email);
        user.setEmailHmac(emailHmac);
        user.setPasswordHash("$2a$10$hashedPasswordHere");
        user.setActive(true);
        user.setFailedLoginAttempts(1);

        when(hmacSha256Hasher.hashEmail(email)).thenReturn(emailHmac);
        when(userAccountRepository.findByEmailHmac(emailHmac)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("WrongPassword#", user.getPasswordHash())).thenReturn(false);

        ApiException ex = assertThrows(ApiException.class, () ->
                authService.login(new LoginRequest(email, "WrongPassword#"), "127.0.0.1")
        );

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
        assertEquals("Invalid email or password", ex.getMessage());
        assertEquals(2, user.getFailedLoginAttempts());
        verify(userAccountRepository).save(user);
    }

    @Test
    @DisplayName("Account locks for 15 minutes on 5th failed attempt with generic error")
    void testFifthFailedLoginLocksAccount() {
        String email = "nimal@lankacare.lk";
        String emailHmac = "mock-hmac-1234567890";

        UserAccount user = new UserAccount();
        user.setId(UUID.randomUUID());
        user.setEmail(email);
        user.setEmailHmac(emailHmac);
        user.setPasswordHash("$2a$10$hashedPasswordHere");
        user.setActive(true);
        user.setFailedLoginAttempts(4); // 4th attempt previously

        when(hmacSha256Hasher.hashEmail(email)).thenReturn(emailHmac);
        when(userAccountRepository.findByEmailHmac(emailHmac)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("WrongPassword#", user.getPasswordHash())).thenReturn(false);

        ApiException ex = assertThrows(ApiException.class, () ->
                authService.login(new LoginRequest(email, "WrongPassword#"), "127.0.0.1")
        );

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
        assertEquals("Invalid email or password", ex.getMessage());
        assertEquals(5, user.getFailedLoginAttempts());
        assertNotNull(user.getLockedUntil());
        assertTrue(user.getLockedUntil().isAfter(Instant.now()));
        verify(userAccountRepository).save(user);
    }

    @Test
    @DisplayName("Locked account cannot login and returns generic error")
    void testLockedAccountCannotLogin() {
        String email = "nimal@lankacare.lk";
        String emailHmac = "mock-hmac-1234567890";

        UserAccount user = new UserAccount();
        user.setId(UUID.randomUUID());
        user.setEmail(email);
        user.setEmailHmac(emailHmac);
        user.setActive(true);
        user.setLockedUntil(Instant.now().plusSeconds(600)); // locked for next 10 minutes

        when(hmacSha256Hasher.hashEmail(email)).thenReturn(emailHmac);
        when(userAccountRepository.findByEmailHmac(emailHmac)).thenReturn(Optional.of(user));

        ApiException ex = assertThrows(ApiException.class, () ->
                authService.login(new LoginRequest(email, "AnyPassword#"), "127.0.0.1")
        );

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
        assertEquals("Invalid email or password", ex.getMessage());
        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    @Test
    @DisplayName("Non-existent email runs dummy bcrypt check and returns generic error")
    void testUnknownEmailRunsDummyBcrypt() {
        String email = "nonexistent@lankacare.lk";
        String emailHmac = "mock-hmac-unknown";

        when(hmacSha256Hasher.hashEmail(email)).thenReturn(emailHmac);
        when(userAccountRepository.findByEmailHmac(emailHmac)).thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class, () ->
                authService.login(new LoginRequest(email, "SecretPassword#1"), "127.0.0.1")
        );

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
        assertEquals("Invalid email or password", ex.getMessage());
        verify(passwordEncoder).matches(eq("SecretPassword#1"), anyString());
    }
}
