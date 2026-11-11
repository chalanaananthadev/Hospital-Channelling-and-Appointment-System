package com.medichannel.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medichannel.dto.common.ApiError;
import com.medichannel.enums.Role;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

@Component
public class MedicalPrivacyFilter extends OncePerRequestFilter {

    private static final List<String> CLINICAL_PREFIXES = List.of(
            "/api/consultations",
            "/api/chat",
            "/api/clinical"
    );

    private final ObjectMapper objectMapper;
    private final MedicalPrivacyGuard medicalPrivacyGuard;

    public MedicalPrivacyFilter(ObjectMapper objectMapper, MedicalPrivacyGuard medicalPrivacyGuard) {
        this.objectMapper = objectMapper;
        this.medicalPrivacyGuard = medicalPrivacyGuard;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (isClinicalPath(request.getRequestURI())) {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser principal
                    && medicalPrivacyGuard.isClinicalRoleBlocked(principal.getRole())) {
                deny(request, response);
                return;
            }
            if (authentication != null && authentication.getAuthorities().stream().anyMatch(a -> {
                try {
                    String role = a.getAuthority().replace("ROLE_", "");
                    return medicalPrivacyGuard.isClinicalRoleBlocked(Role.valueOf(role));
                } catch (IllegalArgumentException ignored) {
                    return false;
                }
            })) {
                deny(request, response);
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private boolean isClinicalPath(String uri) {
        String path = uri.toLowerCase(Locale.ROOT);
        return CLINICAL_PREFIXES.stream().anyMatch(path::startsWith);
    }

    private void deny(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiError error = new ApiError(
                Instant.now(),
                HttpStatus.FORBIDDEN.value(),
                HttpStatus.FORBIDDEN.getReasonPhrase(),
                "Clinical records are restricted",
                request.getRequestURI(),
                null
        );
        objectMapper.writeValue(response.getOutputStream(), error);
    }
}
