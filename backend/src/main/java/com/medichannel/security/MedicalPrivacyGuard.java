package com.medichannel.security;

import com.medichannel.entity.UserAccount;
import com.medichannel.enums.Role;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Server-side medical privacy rule: ADMIN and MANAGER must never read
 * consultation notes, prescriptions, reasons for visit, or chat messages.
 * Clinical data is visible only to the patient and the treating doctor.
 */
@Component
public class MedicalPrivacyGuard {

    public boolean isClinicalRoleBlocked(Role role) {
        return role == Role.ADMIN || role == Role.MANAGER;
    }

    public void assertNotStaffClinicalAccess(UserAccount user) {
        if (user == null || isClinicalRoleBlocked(user.getRole())) {
            throw new AccessDeniedException("Clinical records are restricted");
        }
    }

    public void assertCanReadClinical(UserAccount actor, UUID patientUserId, UUID doctorUserId) {
        assertNotStaffClinicalAccess(actor);
        if (actor.getRole() == Role.CUSTOMER && !actor.getId().equals(patientUserId)) {
            throw new AccessDeniedException("Clinical records are restricted");
        }
        if (actor.getRole() == Role.DOCTOR && !actor.getId().equals(doctorUserId)) {
            throw new AccessDeniedException("Clinical records are restricted");
        }
    }
}
