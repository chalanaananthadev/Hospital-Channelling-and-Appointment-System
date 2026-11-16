package com.medichannel.service;

import com.medichannel.dto.auth.AuthResponse;
import com.medichannel.dto.auth.LoginRequest;
import com.medichannel.dto.auth.MeResponse;
import com.medichannel.dto.auth.RegisterPatientRequest;
import com.medichannel.entity.Patient;
import com.medichannel.entity.UserAccount;
import com.medichannel.enums.AuditAction;
import com.medichannel.enums.Role;
import com.medichannel.exception.ApiException;
import com.medichannel.repository.PatientRepository;
import com.medichannel.repository.UserAccountRepository;
import com.medichannel.security.HmacSha256Hasher;
import com.medichannel.security.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.Duration;

@Service
public class AuthService {

    static final String GENERIC_LOGIN_ERROR = "Invalid email or password";
    private static final String DUMMY_BCRYPT = "$2a$10$7EqJtq98hPqEX7fNZaYDKO0dCnQ6N/j/3m.kYg3aQ1v3nG2p6q2eW";

    private final UserAccountRepository userAccountRepository;
    private final PatientRepository patientRepository;
    private final PasswordEncoder passwordEncoder;
    private final HmacSha256Hasher hmacSha256Hasher;
    private final JwtService jwtService;
    private final AuditService auditService;
    private final int maxAttempts;
    private final Duration lockDuration;

    public AuthService(
            UserAccountRepository userAccountRepository,
            PatientRepository patientRepository,
            PasswordEncoder passwordEncoder,
            HmacSha256Hasher hmacSha256Hasher,
            JwtService jwtService,
            AuditService auditService,
            @Value("${medichannel.lockout.max-attempts}") int maxAttempts,
            @Value("${medichannel.lockout.duration-minutes}") long lockMinutes
    ) {
        this.userAccountRepository = userAccountRepository;
        this.patientRepository = patientRepository;
        this.passwordEncoder = passwordEncoder;
        this.hmacSha256Hasher = hmacSha256Hasher;
        this.jwtService = jwtService;
        this.auditService = auditService;
        this.maxAttempts = maxAttempts;
        this.lockDuration = Duration.ofMinutes(lockMinutes);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public AuthResponse login(LoginRequest request, String ipAddress) {
        String emailHmac = hmacSha256Hasher.hashEmail(request.email());
        UserAccount user = userAccountRepository.findByEmailHmac(emailHmac).orElse(null);

        if (user == null) {
            passwordEncoder.matches(request.password(), DUMMY_BCRYPT);
            throw invalidLogin();
        }

        Instant now = Instant.now();
        if (user.isLocked(now) || !user.isActive()) {
            auditService.record(user, AuditAction.LOGIN_FAILURE, "USER", user.getId(), ipAddress);
            throw invalidLogin();
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            int attempts = user.getFailedLoginAttempts() + 1;
            user.setFailedLoginAttempts(attempts);
            if (attempts >= maxAttempts) {
                user.setLockedUntil(now.plus(lockDuration));
                auditService.record(user, AuditAction.ACCOUNT_LOCKED, "USER", user.getId(), ipAddress);
            }
            userAccountRepository.save(user);
            auditService.record(user, AuditAction.LOGIN_FAILURE, "USER", user.getId(), ipAddress);
            throw invalidLogin();
        }

        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(now);
        userAccountRepository.save(user);
        auditService.record(user, AuditAction.LOGIN_SUCCESS, "USER", user.getId(), ipAddress);

        return toAuthResponse(user);
    }

    @Transactional
    public AuthResponse registerPatient(RegisterPatientRequest request, String ipAddress) {
        String emailHmac = hmacSha256Hasher.hashEmail(request.email());
        String nicHmac = hmacSha256Hasher.hashNic(request.nic());

        if (userAccountRepository.existsByEmailHmac(emailHmac) || patientRepository.existsByNicHmac(nicHmac)) {
            throw new ApiException(HttpStatus.CONFLICT, "An account with these details already exists");
        }

        try {
            UserAccount user = new UserAccount();
            user.setEmail(HmacSha256Hasher.normaliseEmail(request.email()));
            user.setEmailHmac(emailHmac);
            user.setFullName(request.fullName().trim());
            user.setPasswordHash(passwordEncoder.encode(request.password()));
            user.setRole(Role.CUSTOMER);
            userAccountRepository.save(user);

            Patient patient = new Patient();
            patient.setUser(user);
            patient.setNic(HmacSha256Hasher.normaliseNic(request.nic()));
            patient.setNicHmac(nicHmac);
            patient.setDateOfBirth(request.dateOfBirth().toString());
            patient.setGender(request.gender());
            patient.setPhone(request.phone().trim());
            patient.setAddress(request.address().trim());
            patientRepository.save(patient);

            auditService.record(user, AuditAction.ACCOUNT_CREATED, "USER", user.getId(), ipAddress);
            return toAuthResponse(user);
        } catch (DataIntegrityViolationException ex) {
            throw new ApiException(HttpStatus.CONFLICT, "An account with these details already exists");
        }
    }

    @Transactional(readOnly = true)
    public MeResponse me(UserAccount user) {
        return new MeResponse(
                user.getId(),
                user.getRole(),
                user.getFullName(),
                user.getEmail(),
                user.isMustChangePassword()
        );
    }

    private AuthResponse toAuthResponse(UserAccount user) {
        return new AuthResponse(
                jwtService.issueToken(user),
                user.getId(),
                user.getRole(),
                user.getFullName(),
                user.isMustChangePassword()
        );
    }

    private ApiException invalidLogin() {
        return new ApiException(HttpStatus.UNAUTHORIZED, GENERIC_LOGIN_ERROR);
    }
}
