package com.medichannel.controller;

import com.medichannel.dto.session.PublicSessionDto;
import com.medichannel.enums.SessionType;
import com.medichannel.service.ChannelSessionService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/public/sessions")
public class PublicSessionController {

    private final ChannelSessionService sessionService;

    public PublicSessionController(ChannelSessionService sessionService) {
        this.sessionService = sessionService;
    }

    @GetMapping
    public ResponseEntity<List<PublicSessionDto>> searchSessions(
            @RequestParam(required = false) UUID doctorId,
            @RequestParam(required = false) UUID specialisationId,
            @RequestParam(required = false) String doctorName,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) SessionType sessionType
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(sessionService.searchPublicSessions(doctorId, specialisationId, doctorName, date, sessionType));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PublicSessionDto> getSession(@PathVariable UUID id) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(sessionService.getPublicSession(id));
    }
}