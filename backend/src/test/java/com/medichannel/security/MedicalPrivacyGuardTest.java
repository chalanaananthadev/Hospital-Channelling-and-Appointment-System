package com.medichannel.security;

import com.medichannel.entity.UserAccount;
import com.medichannel.enums.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MedicalPrivacyGuardTest {

    private MedicalPrivacyGuard guard;

    @BeforeEach
    void setUp() {
        guard = new MedicalPrivacyGuard();
    }

    @Test
    @DisplayName("Admin and Manager roles are flagged as blocked from clinical access")
    void testIsClinicalRoleBlocked() {
        assertTrue(guard.isClinicalRoleBlocked(Role.ADMIN));
        assertTrue(guard.isClinicalRoleBlocked(Role.MANAGER));
        assertFalse(guard.isClinicalRoleBlocked(Role.DOCTOR));
        assertFalse(guard.isClinicalRoleBlocked(Role.CUSTOMER));
    }

    @Test
    @DisplayName("Admin or Manager attempting clinical access throws AccessDeniedException")
    void testStaffClinicalAccessDenied() {
        UserAccount admin = new UserAccount();
        admin.setRole(Role.ADMIN);

        UserAccount manager = new UserAccount();
        manager.setRole(Role.MANAGER);

        assertThrows(AccessDeniedException.class, () -> guard.assertNotStaffClinicalAccess(admin));
        assertThrows(AccessDeniedException.class, () -> guard.assertNotStaffClinicalAccess(manager));
        assertThrows(AccessDeniedException.class, () -> guard.assertNotStaffClinicalAccess(null));
    }

    @Test
    @DisplayName("Treating doctor and owning patient can access clinical notes, other users cannot")
    void testCanReadClinical() {
        UUID patientUserId = UUID.randomUUID();
        UUID doctorUserId = UUID.randomUUID();
        UUID otherPatientUserId = UUID.randomUUID();
        UUID otherDoctorUserId = UUID.randomUUID();

        UserAccount patient = new UserAccount();
        patient.setId(patientUserId);
        patient.setRole(Role.CUSTOMER);

        UserAccount doctor = new UserAccount();
        doctor.setId(doctorUserId);
        doctor.setRole(Role.DOCTOR);

        UserAccount otherPatient = new UserAccount();
        otherPatient.setId(otherPatientUserId);
        otherPatient.setRole(Role.CUSTOMER);

        UserAccount otherDoctor = new UserAccount();
        otherDoctor.setId(otherDoctorUserId);
        otherDoctor.setRole(Role.DOCTOR);

        UserAccount admin = new UserAccount();
        admin.setRole(Role.ADMIN);

        // Allowed
        assertDoesNotThrow(() -> guard.assertCanReadClinical(patient, patientUserId, doctorUserId));
        assertDoesNotThrow(() -> guard.assertCanReadClinical(doctor, patientUserId, doctorUserId));

        // Denied
        assertThrows(AccessDeniedException.class, () -> guard.assertCanReadClinical(otherPatient, patientUserId, doctorUserId));
        assertThrows(AccessDeniedException.class, () -> guard.assertCanReadClinical(otherDoctor, patientUserId, doctorUserId));
        assertThrows(AccessDeniedException.class, () -> guard.assertCanReadClinical(admin, patientUserId, doctorUserId));
    }
}
