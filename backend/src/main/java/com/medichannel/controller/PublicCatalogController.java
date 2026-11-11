package com.medichannel.controller;

import com.medichannel.dto.catalog.NoticeDto;
import com.medichannel.dto.catalog.PublicDoctorDto;
import com.medichannel.dto.catalog.SpecialisationDto;
import com.medichannel.service.PublicCatalogService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/public")
public class PublicCatalogController {

    private final PublicCatalogService publicCatalogService;

    public PublicCatalogController(PublicCatalogService publicCatalogService) {
        this.publicCatalogService = publicCatalogService;
    }

    @GetMapping("/specialisations")
    public ResponseEntity<List<SpecialisationDto>> specialisations() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(2)).cachePublic())
                .body(publicCatalogService.listSpecialisations());
    }

    @GetMapping("/doctors")
    public ResponseEntity<List<PublicDoctorDto>> doctors(
            @RequestParam(required = false) UUID specialisationId,
            @RequestParam(required = false) String name
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(2)).cachePublic())
                .body(publicCatalogService.searchDoctors(specialisationId, name));
    }

    @GetMapping("/notices")
    public ResponseEntity<List<NoticeDto>> notices() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(2)).cachePublic())
                .body(publicCatalogService.listNotices());
    }
}
