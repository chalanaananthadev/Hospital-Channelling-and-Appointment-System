package com.medichannel.controller;

import com.medichannel.security.AuthenticatedUser;
import com.medichannel.security.MedicalPrivacyGuard;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Placeholder clinical endpoints so privacy rules can be verified before
 * Members 2–4 implement consultations and chat.
 */
@RestController
@RequestMapping("/api/clinical")
public class ClinicalGuardController {

    private final MedicalPrivacyGuard medicalPrivacyGuard;

    public ClinicalGuardController(MedicalPrivacyGuard medicalPrivacyGuard) {
        this.medicalPrivacyGuard = medicalPrivacyGuard;
    }

    @GetMapping("/notes")
    public ResponseEntity<Map<String, String>> notes(@AuthenticationPrincipal AuthenticatedUser principal) {
        medicalPrivacyGuard.assertNotStaffClinicalAccess(principal.getUser());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(Map.of("status", "restricted-to-patient-and-doctor"));
    }
}
