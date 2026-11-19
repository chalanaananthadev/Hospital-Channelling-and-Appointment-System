package com.medichannel.service;

import com.medichannel.dto.common.PageResponse;
import com.medichannel.dto.manager.DoctorCreateRequest;
import com.medichannel.dto.manager.DoctorResponse;
import com.medichannel.dto.manager.DoctorUpdateRequest;
import com.medichannel.dto.manager.DoctorUserOptionDto;
import com.medichannel.dto.manager.RoomRequest;
import com.medichannel.dto.manager.RoomResponse;
import com.medichannel.dto.manager.SpecialisationRequest;
import com.medichannel.dto.manager.SpecialisationResponse;
import com.medichannel.entity.Doctor;
import com.medichannel.entity.Room;
import com.medichannel.entity.Specialisation;
import com.medichannel.entity.UserAccount;
import com.medichannel.enums.Role;
import com.medichannel.exception.ApiException;
import com.medichannel.repository.DoctorRepository;
import com.medichannel.repository.RoomRepository;
import com.medichannel.repository.SpecialisationRepository;
import com.medichannel.repository.UserAccountRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class ManagerCatalogService {

    private final SpecialisationRepository specialisationRepository;
    private final RoomRepository roomRepository;
    private final DoctorRepository doctorRepository;
    private final UserAccountRepository userAccountRepository;

    public ManagerCatalogService(
            SpecialisationRepository specialisationRepository,
            RoomRepository roomRepository,
            DoctorRepository doctorRepository,
            UserAccountRepository userAccountRepository
    ) {
        this.specialisationRepository = specialisationRepository;
        this.roomRepository = roomRepository;
        this.doctorRepository = doctorRepository;
        this.userAccountRepository = userAccountRepository;
    }

    // ==================== SPECIALISATIONS ====================

    @Transactional(readOnly = true)
    public PageResponse<SpecialisationResponse> listSpecialisations(String search, int page, int size) {
        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                clampPageSize(size),
                Sort.by(Sort.Direction.ASC, "name")
        );
        Page<Specialisation> result = (search == null || search.isBlank())
                ? specialisationRepository.findAll(pageable)
                : specialisationRepository.findByNameContainingIgnoreCase(search.trim(), pageable);
        return PageResponse.from(result.map(this::toSpecialisationResponse));
    }

    @Transactional(readOnly = true)
    public List<SpecialisationResponse> listActiveSpecialisations() {
        return specialisationRepository.findByActiveTrueOrderByNameAsc().stream()
                .map(this::toSpecialisationResponse)
                .toList();
    }

    @CacheEvict(value = {"publicSpecialisations", "publicDoctors"}, allEntries = true)
    @Transactional
    public SpecialisationResponse createSpecialisation(SpecialisationRequest request) {
        String name = request.name().trim();
        if (specialisationRepository.findByNameIgnoreCase(name).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "A specialisation with this name already exists");
        }
        Specialisation specialisation = new Specialisation();
        specialisation.setName(name);
        specialisation.setDescription(trimToNull(request.description()));
        specialisation.setActive(true);
        return toSpecialisationResponse(specialisationRepository.save(specialisation));
    }

    @CacheEvict(value = {"publicSpecialisations", "publicDoctors"}, allEntries = true)
    @Transactional
    public SpecialisationResponse updateSpecialisation(UUID id, SpecialisationRequest request) {
        Specialisation specialisation = specialisationRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Specialisation not found"));
        String name = request.name().trim();
        if (specialisationRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new ApiException(HttpStatus.CONFLICT, "A specialisation with this name already exists");
        }
        specialisation.setName(name);
        specialisation.setDescription(trimToNull(request.description()));
        return toSpecialisationResponse(specialisationRepository.save(specialisation));
    }

    @CacheEvict(value = {"publicSpecialisations", "publicDoctors"}, allEntries = true)
    @Transactional
    public SpecialisationResponse updateSpecialisationStatus(UUID id, boolean active) {
        Specialisation specialisation = specialisationRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Specialisation not found"));
        specialisation.setActive(active);
        return toSpecialisationResponse(specialisationRepository.save(specialisation));
    }

    // ==================== ROOMS ====================

    @Transactional(readOnly = true)
    public PageResponse<RoomResponse> listRooms(String search, int page, int size) {
        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                clampPageSize(size),
                Sort.by(Sort.Direction.ASC, "name")
        );
        Page<Room> result = (search == null || search.isBlank())
                ? roomRepository.findAll(pageable)
                : roomRepository.findByNameContainingIgnoreCase(search.trim(), pageable);
        return PageResponse.from(result.map(this::toRoomResponse));
    }

    @Transactional(readOnly = true)
    public List<RoomResponse> listActiveRooms() {
        return roomRepository.findByActiveTrueOrderByNameAsc().stream()
                .map(this::toRoomResponse)
                .toList();
    }

    @Transactional
    public RoomResponse createRoom(RoomRequest request) {
        String name = request.name().trim();
        if (roomRepository.findByNameIgnoreCase(name).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "A room with this name already exists");
        }
        Room room = new Room();
        room.setName(name);
        room.setLocation(trimToNull(request.location()));
        room.setActive(true);
        return toRoomResponse(roomRepository.save(room));
    }

    @Transactional
    public RoomResponse updateRoom(UUID id, RoomRequest request) {
        Room room = roomRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Room not found"));
        String name = request.name().trim();
        if (roomRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new ApiException(HttpStatus.CONFLICT, "A room with this name already exists");
        }
        room.setName(name);
        room.setLocation(trimToNull(request.location()));
        return toRoomResponse(roomRepository.save(room));
    }

    @Transactional
    public RoomResponse updateRoomStatus(UUID id, boolean active) {
        Room room = roomRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Room not found"));
        room.setActive(active);
        return toRoomResponse(roomRepository.save(room));
    }

    // ==================== DOCTORS ====================

    @Transactional(readOnly = true)
    public PageResponse<DoctorResponse> listDoctors(
            String search,
            UUID specialisationId,
            Boolean active,
            int page,
            int size
    ) {
        String needle = (search == null || search.isBlank()) ? null : search.trim().toLowerCase(Locale.ROOT);
        List<DoctorResponse> filtered = doctorRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(d -> specialisationId == null || d.getSpecialisation().getId().equals(specialisationId))
                .filter(d -> active == null || d.isActive() == active)
                .map(this::toDoctorResponse)
                .filter(d -> needle == null
                        || containsIgnoreCase(d.fullName(), needle)
                        || containsIgnoreCase(d.slmcNumber(), needle)
                        || containsIgnoreCase(d.specialisationName(), needle))
                .toList();

        int safePage = Math.max(page, 0);
        int safeSize = clampPageSize(size);
        int total = filtered.size();
        int fromIndex = Math.min(safePage * safeSize, total);
        int toIndex = Math.min(fromIndex + safeSize, total);
        int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / safeSize);

        return new PageResponse<>(
                List.copyOf(filtered.subList(fromIndex, toIndex)),
                safePage,
                safeSize,
                total,
                totalPages
        );
    }

    @Transactional(readOnly = true)
    public DoctorResponse getDoctor(UUID id) {
        Doctor doctor = doctorRepository.findWithDetailsById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Doctor profile not found"));
        return toDoctorResponse(doctor);
    }

    @Transactional(readOnly = true)
    public List<DoctorUserOptionDto> listUnassignedDoctorUsers() {
        return doctorRepository.findUnassignedDoctorUsers().stream()
                .map(u -> new DoctorUserOptionDto(u.getId(), u.getFullName(), u.getEmail()))
                .sorted(Comparator.comparing(DoctorUserOptionDto::fullName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @CacheEvict(value = {"publicDoctors", "publicSpecialisations"}, allEntries = true)
    @Transactional
    public DoctorResponse createDoctor(DoctorCreateRequest request) {
        UserAccount user = userAccountRepository.findById(request.userId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Doctor user account not found"));

        if (user.getRole() != Role.DOCTOR) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Selected user account does not have the DOCTOR role");
        }
        if (doctorRepository.existsByUserId(user.getId())) {
            throw new ApiException(HttpStatus.CONFLICT, "This user account already has a doctor profile");
        }

        Specialisation specialisation = specialisationRepository.findById(request.specialisationId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Specialisation not found"));
        if (!specialisation.isActive()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Cannot assign an inactive specialisation");
        }

        String normalizedSlmc = normalizeSlmc(request.slmcNumber());
        if (doctorRepository.findBySlmcNumberIgnoreCase(normalizedSlmc).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "A doctor with this SLMC registration number already exists");
        }

        user.setFullName(request.fullName().trim());
        userAccountRepository.save(user);

        Doctor doctor = new Doctor();
        doctor.setUser(user);
        doctor.setSpecialisation(specialisation);
        doctor.setSlmcNumber(normalizedSlmc);
        doctor.setQualifications(request.qualifications().trim());
        doctor.setConsultationFee(request.consultationFee());
        doctor.setBio(trimToNull(request.bio()));
        doctor.setActive(true);

        return toDoctorResponse(doctorRepository.save(doctor));
    }

    @CacheEvict(value = {"publicDoctors", "publicSpecialisations"}, allEntries = true)
    @Transactional
    public DoctorResponse updateDoctor(UUID id, DoctorUpdateRequest request) {
        Doctor doctor = doctorRepository.findWithDetailsById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Doctor profile not found"));

        Specialisation specialisation = specialisationRepository.findById(request.specialisationId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Specialisation not found"));
        boolean specialisationChanged = !specialisation.getId().equals(doctor.getSpecialisation().getId());
        if (specialisationChanged && !specialisation.isActive()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Cannot assign an inactive specialisation");
        }

        String normalizedSlmc = normalizeSlmc(request.slmcNumber());
        if (doctorRepository.existsBySlmcNumberIgnoreCaseAndIdNot(normalizedSlmc, id)) {
            throw new ApiException(HttpStatus.CONFLICT, "A doctor with this SLMC registration number already exists");
        }

        UserAccount user = doctor.getUser();
        user.setFullName(request.fullName().trim());
        userAccountRepository.save(user);

        doctor.setSpecialisation(specialisation);
        doctor.setSlmcNumber(normalizedSlmc);
        doctor.setQualifications(request.qualifications().trim());
        doctor.setConsultationFee(request.consultationFee());
        doctor.setBio(trimToNull(request.bio()));

        return toDoctorResponse(doctorRepository.save(doctor));
    }

    @CacheEvict(value = {"publicDoctors", "publicSpecialisations"}, allEntries = true)
    @Transactional
    public DoctorResponse updateDoctorStatus(UUID id, boolean active) {
        Doctor doctor = doctorRepository.findWithDetailsById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Doctor profile not found"));
        doctor.setActive(active);
        return toDoctorResponse(doctorRepository.save(doctor));
    }

    // ==================== HELPERS ====================

    private String normalizeSlmc(String raw) {
        String upper = raw.trim().toUpperCase(Locale.ROOT).replace(" ", "-");
        if (upper.startsWith("SLMC-")) {
            return upper;
        }
        if (upper.startsWith("SLMC")) {
            return "SLMC-" + upper.substring(4);
        }
        return "SLMC-" + upper;
    }

    private int clampPageSize(int size) {
        return Math.min(Math.max(size, 1), 100);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean containsIgnoreCase(String value, String lowerNeedle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(lowerNeedle);
    }

    private SpecialisationResponse toSpecialisationResponse(Specialisation s) {
        return new SpecialisationResponse(
                s.getId(),
                s.getName(),
                s.getDescription(),
                s.isActive(),
                s.getCreatedAt(),
                s.getUpdatedAt()
        );
    }

    private RoomResponse toRoomResponse(Room r) {
        return new RoomResponse(
                r.getId(),
                r.getName(),
                r.getLocation(),
                r.isActive(),
                r.getCreatedAt(),
                r.getUpdatedAt()
        );
    }

    private DoctorResponse toDoctorResponse(Doctor d) {
        return new DoctorResponse(
                d.getId(),
                d.getUser().getId(),
                d.getUser().getFullName(),
                d.getUser().getEmail(),
                d.getSpecialisation().getId(),
                d.getSpecialisation().getName(),
                d.getSlmcNumber(),
                d.getQualifications(),
                d.getConsultationFee(),
                d.getBio(),
                d.isActive(),
                d.getCreatedAt(),
                d.getUpdatedAt()
        );
    }
}