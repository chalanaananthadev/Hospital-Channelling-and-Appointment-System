package com.medichannel.controller;

import com.medichannel.dto.common.PageResponse;
import com.medichannel.dto.manager.DoctorCreateRequest;
import com.medichannel.dto.manager.DoctorResponse;
import com.medichannel.dto.manager.DoctorUpdateRequest;
import com.medichannel.dto.manager.DoctorUserOptionDto;
import com.medichannel.dto.manager.RoomRequest;
import com.medichannel.dto.manager.RoomResponse;
import com.medichannel.dto.manager.SpecialisationRequest;
import com.medichannel.dto.manager.SpecialisationResponse;
import com.medichannel.dto.manager.StatusUpdateRequest;
import com.medichannel.service.ManagerCatalogService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/manager")
@PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
public class ManagerCatalogController {

    private final ManagerCatalogService managerCatalogService;

    public ManagerCatalogController(ManagerCatalogService managerCatalogService) {
        this.managerCatalogService = managerCatalogService;
    }

    // ==================== SPECIALISATIONS ====================

    @GetMapping("/specialisations")
    public PageResponse<SpecialisationResponse> listSpecialisations(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return managerCatalogService.listSpecialisations(search, page, size);
    }

    @GetMapping("/specialisations/active")
    public List<SpecialisationResponse> listActiveSpecialisations() {
        return managerCatalogService.listActiveSpecialisations();
    }

    @PostMapping("/specialisations")
    public ResponseEntity<SpecialisationResponse> createSpecialisation(
            @Valid @RequestBody SpecialisationRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(managerCatalogService.createSpecialisation(request));
    }

    @PutMapping("/specialisations/{id}")
    public SpecialisationResponse updateSpecialisation(
            @PathVariable UUID id,
            @Valid @RequestBody SpecialisationRequest request
    ) {
        return managerCatalogService.updateSpecialisation(id, request);
    }

    @PatchMapping("/specialisations/{id}/status")
    public SpecialisationResponse updateSpecialisationStatus(
            @PathVariable UUID id,
            @Valid @RequestBody StatusUpdateRequest request
    ) {
        return managerCatalogService.updateSpecialisationStatus(id, request.active());
    }

    // ==================== ROOMS ====================

    @GetMapping("/rooms")
    public PageResponse<RoomResponse> listRooms(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return managerCatalogService.listRooms(search, page, size);
    }

    @GetMapping("/rooms/active")
    public List<RoomResponse> listActiveRooms() {
        return managerCatalogService.listActiveRooms();
    }

    @PostMapping("/rooms")
    public ResponseEntity<RoomResponse> createRoom(@Valid @RequestBody RoomRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(managerCatalogService.createRoom(request));
    }

    @PutMapping("/rooms/{id}")
    public RoomResponse updateRoom(@PathVariable UUID id, @Valid @RequestBody RoomRequest request) {
        return managerCatalogService.updateRoom(id, request);
    }

    @PatchMapping("/rooms/{id}/status")
    public RoomResponse updateRoomStatus(
            @PathVariable UUID id,
            @Valid @RequestBody StatusUpdateRequest request
    ) {
        return managerCatalogService.updateRoomStatus(id, request.active());
    }

    // ==================== DOCTORS ====================

    @GetMapping("/doctors")
    public PageResponse<DoctorResponse> listDoctors(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UUID specialisationId,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return managerCatalogService.listDoctors(search, specialisationId, active, page, size);
    }

    @GetMapping("/doctors/available-users")
    public List<DoctorUserOptionDto> listUnassignedDoctorUsers() {
        return managerCatalogService.listUnassignedDoctorUsers();
    }

    @GetMapping("/doctors/{id}")
    public DoctorResponse getDoctor(@PathVariable UUID id) {
        return managerCatalogService.getDoctor(id);
    }

    @PostMapping("/doctors")
    public ResponseEntity<DoctorResponse> createDoctor(@Valid @RequestBody DoctorCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(managerCatalogService.createDoctor(request));
    }

    @PutMapping("/doctors/{id}")
    public DoctorResponse updateDoctor(
            @PathVariable UUID id,
            @Valid @RequestBody DoctorUpdateRequest request
    ) {
        return managerCatalogService.updateDoctor(id, request);
    }

    @PatchMapping("/doctors/{id}/status")
    public DoctorResponse updateDoctorStatus(
            @PathVariable UUID id,
            @Valid @RequestBody StatusUpdateRequest request
    ) {
        return managerCatalogService.updateDoctorStatus(id, request.active());
    }
}