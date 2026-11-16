package com.medichannel.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medichannel.config.SecurityConfig;
import com.medichannel.controller.ClinicalGuardController;
import com.medichannel.controller.HealthController;
import com.medichannel.controller.PublicCatalogController;
import com.medichannel.entity.UserAccount;
import com.medichannel.enums.Role;
import com.medichannel.repository.UserAccountRepository;
import com.medichannel.service.PublicCatalogService;
import io.jsonwebtoken.ExpiredJwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {
        ClinicalGuardController.class,
        HealthController.class,
        PublicCatalogController.class,
        SecurityIntegrationTest.TestRoleEndpointsController.class
})
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        MedicalPrivacyFilter.class,
        MedicalPrivacyGuard.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        SecurityIntegrationTest.JacksonConfig.class
})
@TestPropertySource(properties = {
        "medichannel.cors.allowed-origin=http://localhost:3000"
})
class SecurityIntegrationTest {

    @TestConfiguration
    static class JacksonConfig {
        @Bean
        public ObjectMapper objectMapper() {
            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
            mapper.configure(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
            return mapper;
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserAccountRepository userAccountRepository;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private PublicCatalogService publicCatalogService;

    private UserAccount customerUser;
    private UserAccount doctorUser;
    private UserAccount managerUser;
    private UserAccount adminUser;

    private final UUID customerId = UUID.randomUUID();
    private final UUID doctorId = UUID.randomUUID();
    private final UUID managerId = UUID.randomUUID();
    private final UUID adminId = UUID.randomUUID();

    @RestController
    static class TestRoleEndpointsController {
        @GetMapping("/api/admin/ping")
        public ResponseEntity<String> adminPing() {
            return ResponseEntity.ok("admin-ok");
        }

        @GetMapping("/api/manager/ping")
        public ResponseEntity<String> managerPing() {
            return ResponseEntity.ok("manager-ok");
        }

        @GetMapping("/api/doctor/ping")
        public ResponseEntity<String> doctorPing() {
            return ResponseEntity.ok("doctor-ok");
        }

        @GetMapping("/api/patient/ping")
        public ResponseEntity<String> patientPing() {
            return ResponseEntity.ok("patient-ok");
        }
    }

    @BeforeEach
    void setUp() {
        customerUser = createUser(customerId, Role.CUSTOMER, true, null);
        doctorUser = createUser(doctorId, Role.DOCTOR, true, null);
        managerUser = createUser(managerId, Role.MANAGER, true, null);
        adminUser = createUser(adminId, Role.ADMIN, true, null);

        when(userAccountRepository.findById(customerId)).thenReturn(Optional.of(customerUser));
        when(userAccountRepository.findById(doctorId)).thenReturn(Optional.of(doctorUser));
        when(userAccountRepository.findById(managerId)).thenReturn(Optional.of(managerUser));
        when(userAccountRepository.findById(adminId)).thenReturn(Optional.of(adminUser));

        when(jwtService.extractUserId("token-customer")).thenReturn(customerId);
        when(jwtService.extractUserId("token-doctor")).thenReturn(doctorId);
        when(jwtService.extractUserId("token-manager")).thenReturn(managerId);
        when(jwtService.extractUserId("token-admin")).thenReturn(adminId);
    }

    private UserAccount createUser(UUID id, Role role, boolean active, Instant lockedUntil) {
        UserAccount user = new UserAccount();
        user.setId(id);
        user.setEmail(role.name().toLowerCase() + "@test.lk");
        user.setEmailHmac("hmac-" + role.name());
        user.setRole(role);
        user.setFullName("Test " + role.name());
        user.setActive(active);
        user.setLockedUntil(lockedUntil);
        user.setPasswordHash("$2a$10$fakehash");
        return user;
    }

    @Test
    @DisplayName("Unauthenticated request to /api/clinical/** returns 401")
    void testUnauthenticatedClinicalReturns401() throws Exception {
        mockMvc.perform(get("/api/clinical/notes"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Authenticated CUSTOMER can access clinical notes (returns 200)")
    void testCustomerCanAccessClinicalNotes() throws Exception {
        mockMvc.perform(get("/api/clinical/notes")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token-customer"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("restricted-to-patient-and-doctor"));
    }

    @Test
    @DisplayName("Authenticated DOCTOR can access clinical notes (returns 200)")
    void testDoctorCanAccessClinicalNotes() throws Exception {
        mockMvc.perform(get("/api/clinical/notes")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token-doctor"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("restricted-to-patient-and-doctor"));
    }

    @Test
    @DisplayName("Authenticated MANAGER accessing /api/clinical/** receives 403 Forbidden with MedicalPrivacyGuard message")
    void testManagerAccessingClinicalReturns403WithPrivacyMessage() throws Exception {
        mockMvc.perform(get("/api/clinical/notes")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token-manager"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString("Clinical records are restricted")));
    }

    @Test
    @DisplayName("Authenticated ADMIN accessing /api/clinical/** receives 403 Forbidden with MedicalPrivacyGuard message")
    void testAdminAccessingClinicalReturns403WithPrivacyMessage() throws Exception {
        mockMvc.perform(get("/api/clinical/notes")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token-admin"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString("Clinical records are restricted")));
    }

    @Test
    @DisplayName("/api/admin/** returns 403 for CUSTOMER, DOCTOR, MANAGER and 200/404 (not 403) for ADMIN")
    void testAdminEndpointsAuthorization() throws Exception {
        mockMvc.perform(get("/api/admin/ping")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token-customer"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/ping")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token-doctor"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/ping")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token-manager"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/ping")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token-admin"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("/api/manager/** returns 403 for CUSTOMER, DOCTOR and not-403 for MANAGER, ADMIN")
    void testManagerEndpointsAuthorization() throws Exception {
        mockMvc.perform(get("/api/manager/ping")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token-customer"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/manager/ping")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token-doctor"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/manager/ping")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token-manager"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/manager/ping")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token-admin"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("/api/doctor/** returns 403 for CUSTOMER and not-403 for DOCTOR")
    void testDoctorEndpointsAuthorization() throws Exception {
        mockMvc.perform(get("/api/doctor/ping")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token-customer"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/doctor/ping")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token-doctor"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("/api/patient/** returns 403 for DOCTOR and not-403 for CUSTOMER")
    void testPatientEndpointsAuthorization() throws Exception {
        mockMvc.perform(get("/api/patient/ping")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token-doctor"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/patient/ping")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token-customer"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("/api/public/specialisations and /api/health are reachable without authentication")
    void testPublicEndpointsReachableWithoutAuth() throws Exception {
        when(publicCatalogService.listSpecialisations()).thenReturn(List.of());

        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));

        mockMvc.perform(get("/api/public/specialisations"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Inactive user receives 401")
    void testInactiveUserReceives401() throws Exception {
        UUID inactiveId = UUID.randomUUID();
        UserAccount inactiveUser = createUser(inactiveId, Role.CUSTOMER, false, null);
        when(jwtService.extractUserId("token-inactive")).thenReturn(inactiveId);
        when(userAccountRepository.findById(inactiveId)).thenReturn(Optional.of(inactiveUser));

        mockMvc.perform(get("/api/patient/ping")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token-inactive"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Locked user receives 401")
    void testLockedUserReceives401() throws Exception {
        UUID lockedId = UUID.randomUUID();
        UserAccount lockedUser = createUser(lockedId, Role.CUSTOMER, true, Instant.now().plusSeconds(900));
        when(jwtService.extractUserId("token-locked")).thenReturn(lockedId);
        when(userAccountRepository.findById(lockedId)).thenReturn(Optional.of(lockedUser));

        mockMvc.perform(get("/api/patient/ping")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token-locked"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Expired or invalid JWT receives 401")
    void testExpiredJwtReceives401() throws Exception {
        when(jwtService.extractUserId("token-expired"))
                .thenThrow(new ExpiredJwtException(null, null, "Token has expired"));

        mockMvc.perform(get("/api/patient/ping")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token-expired"))
                .andExpect(status().isUnauthorized());
    }
}
