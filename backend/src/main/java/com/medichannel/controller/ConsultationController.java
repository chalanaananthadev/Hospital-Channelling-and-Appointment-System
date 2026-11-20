package com.medichannel.controller;

import com.medichannel.dto.consultation.ConsultationResponse;
import com.medichannel.dto.consultation.ConsultationUpsertRequest;
import com.medichannel.security.AuthenticatedUser;
import com.medichannel.service.ConsultationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/consultations")
@PreAuthorize("hasAnyRole('DOCTOR', 'CUSTOMER')")
public class ConsultationController {

    private final ConsultationService consultationService;

    public ConsultationController(ConsultationService consultationService) {
        this.consultationService = consultationService;
    }

    @GetMapping("/appointments/{appointmentId}")
    public ResponseEntity<ConsultationResponse> getByAppointment(
            @PathVariable UUID appointmentId,
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            HttpServletRequest httpRequest
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(consultationService.getByAppointmentId(
                        appointmentId,
                        currentUser.getUser(),
                        extractClientIp(httpRequest)
                ));
    }

    @PutMapping("/appointments/{appointmentId}")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<ConsultationResponse> saveNotes(
            @PathVariable UUID appointmentId,
            @Valid @RequestBody ConsultationUpsertRequest request,
            @AuthenticationPrincipal AuthenticatedUser currentUser
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(consultationService.saveNotes(appointmentId, request, currentUser.getUser()));
    }

    @PostMapping("/appointments/{appointmentId}/end")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<ConsultationResponse> endConsultation(
            @PathVariable UUID appointmentId,
            @Valid @RequestBody(required = false) ConsultationUpsertRequest request,
            @AuthenticationPrincipal AuthenticatedUser currentUser
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(consultationService.endConsultation(appointmentId, request, currentUser.getUser()));
    }

    private String extractClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}