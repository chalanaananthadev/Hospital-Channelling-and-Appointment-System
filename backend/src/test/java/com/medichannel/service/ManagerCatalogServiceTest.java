package com.medichannel.service;

import com.medichannel.dto.manager.DoctorCreateRequest;
import com.medichannel.dto.manager.DoctorResponse;
import com.medichannel.dto.manager.RoomRequest;
import com.medichannel.dto.manager.SpecialisationRequest;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ManagerCatalogServiceTest {

    @Mock
    private SpecialisationRepository specialisationRepository;
    @Mock
    private RoomRepository roomRepository;
    @Mock
    private DoctorRepository doctorRepository;
    @Mock
    private UserAccountRepository userAccountRepository;

    private ManagerCatalogService service;

    @BeforeEach
    void setUp() {
        service = new ManagerCatalogService(
                specialisationRepository,
                roomRepository,
                doctorRepository,
                userAccountRepository
        );
    }

    @Test
    void createSpecialisationRejectsDuplicateNameWith409() {
        Specialisation existing = new Specialisation();
        existing.setName("Cardiology");
        when(specialisationRepository.findByNameIgnoreCase("Cardiology")).thenReturn(Optional.of(existing));

        ApiException ex = assertThrows(ApiException.class,
                () -> service.createSpecialisation(new SpecialisationRequest("Cardiology", "Heart care")));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
    }

    @Test
    void createRoomRejectsDuplicateNameWith409() {
        Room existing = new Room();
        existing.setName("Room 101");
        when(roomRepository.findByNameIgnoreCase("Room 101")).thenReturn(Optional.of(existing));

        ApiException ex = assertThrows(ApiException.class,
                () -> service.createRoom(new RoomRequest("Room 101", "1st Floor")));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
    }

    @Test
    void createDoctorNormalizesSlmcAndSavesProfile() {
        UUID userId = UUID.randomUUID();
        UUID specId = UUID.randomUUID();

        UserAccount doctorUser = new UserAccount();
        doctorUser.setId(userId);
        doctorUser.setRole(Role.DOCTOR);
        doctorUser.setEmail("dr.silva@lankacare.lk");
        doctorUser.setFullName("Dr. Nimal Silva");

        Specialisation spec = new Specialisation();
        spec.setId(specId);
        spec.setName("Cardiology");
        spec.setActive(true);

        when(userAccountRepository.findById(userId)).thenReturn(Optional.of(doctorUser));
        when(doctorRepository.existsByUserId(userId)).thenReturn(false);
        when(specialisationRepository.findById(specId)).thenReturn(Optional.of(spec));
        when(doctorRepository.findBySlmcNumberIgnoreCase("SLMC-45678")).thenReturn(Optional.empty());
        when(userAccountRepository.save(any(UserAccount.class))).thenAnswer(inv -> inv.getArgument(0));
        when(doctorRepository.save(any(Doctor.class))).thenAnswer(inv -> {
            Doctor saved = inv.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        DoctorCreateRequest request = new DoctorCreateRequest(
                userId,
                "Dr. Nimal Silva",
                specId,
                "45678",
                "MBBS, MD (Colombo)",
                new BigDecimal("3500.00"),
                "Senior Consultant Cardiologist"
        );

        DoctorResponse response = service.createDoctor(request);

        assertEquals("SLMC-45678", response.slmcNumber());
        assertEquals("Cardiology", response.specialisationName());
        assertEquals(new BigDecimal("3500.00"), response.consultationFee());
    }

    @Test
    void createDoctorRejectsNonDoctorUserAccountWith400() {
        UUID userId = UUID.randomUUID();
        UserAccount customer = new UserAccount();
        customer.setId(userId);
        customer.setRole(Role.CUSTOMER);

        when(userAccountRepository.findById(userId)).thenReturn(Optional.of(customer));

        DoctorCreateRequest request = new DoctorCreateRequest(
                userId,
                "Kasun Perera",
                UUID.randomUUID(),
                "SLMC-12345",
                "MBBS",
                new BigDecimal("2500.00"),
                null
        );

        ApiException ex = assertThrows(ApiException.class, () -> service.createDoctor(request));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    @Test
    void updateDoctorStatusDeactivatesWithoutDeletingRecord() {
        UUID doctorId = UUID.randomUUID();

        UserAccount user = new UserAccount();
        user.setId(UUID.randomUUID());
        user.setFullName("Dr. Anoma Fernando");
        user.setEmail("dr.anoma@lankacare.lk");

        Specialisation spec = new Specialisation();
        spec.setId(UUID.randomUUID());
        spec.setName("Paediatrics");

        Doctor doctor = new Doctor();
        doctor.setId(doctorId);
        doctor.setUser(user);
        doctor.setSpecialisation(spec);
        doctor.setSlmcNumber("SLMC-22334");
        doctor.setQualifications("MBBS, DCH");
        doctor.setConsultationFee(new BigDecimal("3000.00"));
        doctor.setActive(true);

        when(doctorRepository.findWithDetailsById(doctorId)).thenReturn(Optional.of(doctor));
        when(doctorRepository.save(any(Doctor.class))).thenAnswer(inv -> inv.getArgument(0));

        DoctorResponse response = service.updateDoctorStatus(doctorId, false);

        assertFalse(response.active());
        verify(doctorRepository).save(doctor);
    }
}