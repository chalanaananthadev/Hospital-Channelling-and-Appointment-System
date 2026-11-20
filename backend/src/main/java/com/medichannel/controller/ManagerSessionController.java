package com.medichannel.controller;

import com.medichannel.dto.common.PageResponse;
import com.medichannel.dto.session.SessionCreateRequest;
import com.medichannel.dto.session.SessionDelayRequest;
import com.medichannel.dto.session.SessionResponse;
import com.medichannel.dto.session.SessionStatusUpdateRequest;
import com.medichannel.dto.session.SessionUpdateRequest;
import com.medichannel.enums.SessionStatus;
import com.medichannel.enums.SessionType;
import com.medichannel.security.AuthenticatedUser;
import com.medichannel.service.ChannelSessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/manager/sessions")
@PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
public class ManagerSessionController {

    private final ChannelSessionService sessionService;

    public ManagerSessionController(ChannelSessionService sessionService) {
        this.sessionService = sessionService;
    }

    @GetMapping
    public PageResponse<SessionResponse> listSessions(
            @RequestParam(required = false) UUID doctorId,
            @RequestParam(required = false) UUID specialisationId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) SessionType sessionType,
            @RequestParam(required = false) SessionStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return sessionService.listManagerSessions(
                doctorId, specialisationId, date, sessionType, status, search, page, size
        );
    }

    @GetMapping("/{id}")
    public SessionResponse getSession(@PathVariable UUID id) {
        return sessionService.getManagerSession(id);
    }

    @PostMapping
    public ResponseEntity<SessionResponse> createSession(@Valid @RequestBody SessionCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(sessionService.createSession(request));
    }

    @PutMapping("/{id}")
    public SessionResponse updateSession(
            @PathVariable UUID id,
            @Valid @RequestBody SessionUpdateRequest request
    ) {
        return sessionService.updateSession(id, request);
    }

    @PatchMapping("/{id}/status")
    public SessionResponse updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody SessionStatusUpdateRequest request
    ) {
        return sessionService.updateSessionStatus(id, request.status());
    }

    @PatchMapping("/{id}/delay")
    public SessionResponse delaySession(
            @PathVariable UUID id,
            @Valid @RequestBody SessionDelayRequest request
    ) {
        return sessionService.delaySession(id, request);
    }

    @PostMapping("/{id}/cancel")
    public SessionResponse cancelSession(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            HttpServletRequest httpRequest
    ) {
        return sessionService.cancelSession(id, currentUser.getUser(), extractClientIp(httpRequest));
    }

    private String extractClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}