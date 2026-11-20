package com.medichannel.controller;

import com.medichannel.dto.doctor.DoctorSessionDto;
import com.medichannel.dto.doctor.DoctorSessionQueueDto;
import com.medichannel.security.AuthenticatedUser;
import com.medichannel.service.DoctorWorkspaceService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/doctor/sessions")
@PreAuthorize("hasRole('DOCTOR')")
public class DoctorWorkspaceController {

    private final DoctorWorkspaceService doctorWorkspaceService;

    public DoctorWorkspaceController(DoctorWorkspaceService doctorWorkspaceService) {
        this.doctorWorkspaceService = doctorWorkspaceService;
    }

    @GetMapping
    public ResponseEntity<List<DoctorSessionDto>> listMySessions(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "false") boolean all
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(doctorWorkspaceService.listMySessions(currentUser.getId(), date, all));
    }

    @GetMapping("/{sessionId}/queue")
    public ResponseEntity<DoctorSessionQueueDto> getSessionQueue(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable UUID sessionId
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(doctorWorkspaceService.getSessionQueue(currentUser.getId(), sessionId));
    }

    @PostMapping("/{sessionId}/start")
    public ResponseEntity<DoctorSessionQueueDto> startSession(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable UUID sessionId
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(doctorWorkspaceService.startSession(currentUser.getId(), sessionId));
    }

    @PostMapping("/{sessionId}/call-next")
    public ResponseEntity<DoctorSessionQueueDto> callNextPatient(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable UUID sessionId
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(doctorWorkspaceService.callNextPatient(currentUser.getId(), sessionId));
    }

    @PostMapping("/{sessionId}/appointments/{appointmentId}/call")
    public ResponseEntity<DoctorSessionQueueDto> callAppointment(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable UUID sessionId,
            @PathVariable UUID appointmentId
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(doctorWorkspaceService.callAppointment(currentUser.getId(), sessionId, appointmentId));
    }

    @PostMapping("/{sessionId}/appointments/{appointmentId}/complete")
    public ResponseEntity<DoctorSessionQueueDto> markCompleted(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable UUID sessionId,
            @PathVariable UUID appointmentId
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(doctorWorkspaceService.markCompleted(currentUser.getId(), sessionId, appointmentId));
    }

    @PostMapping("/{sessionId}/appointments/{appointmentId}/no-show")
    public ResponseEntity<DoctorSessionQueueDto> markNoShow(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable UUID sessionId,
            @PathVariable UUID appointmentId
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(doctorWorkspaceService.markNoShow(currentUser.getId(), sessionId, appointmentId));
    }

    @PostMapping("/{sessionId}/end")
    public ResponseEntity<DoctorSessionQueueDto> endSession(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable UUID sessionId
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(doctorWorkspaceService.endSession(currentUser.getId(), sessionId));
    }
}