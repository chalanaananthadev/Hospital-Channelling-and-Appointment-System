package com.medichannel.service;

import com.medichannel.config.CacheConfig;
import com.medichannel.dto.manager.SpecialisationRequest;
import com.medichannel.entity.Doctor;
import com.medichannel.entity.Specialisation;
import com.medichannel.entity.UserAccount;
import com.medichannel.repository.DoctorRepository;
import com.medichannel.repository.NoticeRepository;
import com.medichannel.repository.RoomRepository;
import com.medichannel.repository.SpecialisationRepository;
import com.medichannel.repository.UserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringJUnitConfig(classes = ManagerCatalogCacheEvictionTest.TestConfig.class)
@TestPropertySource(properties = "medichannel.cache.public-ttl-seconds=60")
class ManagerCatalogCacheEvictionTest {

    @Configuration
    @Import({
            CacheConfig.class,
            PublicDoctorCatalogCache.class,
            PublicCatalogService.class,
            ManagerCatalogService.class
    })
    static class TestConfig {
        @Bean
        static PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer() {
            return new PropertySourcesPlaceholderConfigurer();
        }

        @Bean
        SpecialisationRepository specialisationRepository() {
            return Mockito.mock(SpecialisationRepository.class);
        }

        @Bean
        RoomRepository roomRepository() {
            return Mockito.mock(RoomRepository.class);
        }

        @Bean
        DoctorRepository doctorRepository() {
            return Mockito.mock(DoctorRepository.class);
        }

        @Bean
        UserAccountRepository userAccountRepository() {
            return Mockito.mock(UserAccountRepository.class);
        }

        @Bean
        NoticeRepository noticeRepository() {
            return Mockito.mock(NoticeRepository.class);
        }
    }

    @Autowired
    private PublicCatalogService publicCatalogService;
    @Autowired
    private ManagerCatalogService managerCatalogService;
    @Autowired
    private CacheManager cacheManager;
    @Autowired
    private SpecialisationRepository specialisationRepository;
    @Autowired
    private DoctorRepository doctorRepository;

    @BeforeEach
    void clearCachesAndMocks() {
        cacheManager.getCacheNames().forEach(name -> {
            Cache cache = cacheManager.getCache(name);
            if (cache != null) {
                cache.clear();
            }
        });
        Mockito.reset(specialisationRepository, doctorRepository);
    }

    @Test
    void creatingSpecialisationEvictsPublicSpecialisationsCache() {
        Specialisation spec = new Specialisation();
        spec.setId(UUID.randomUUID());
        spec.setName("Cardiology");
        spec.setActive(true);

        when(specialisationRepository.findByActiveTrueOrderByNameAsc()).thenReturn(List.of(spec));
        when(specialisationRepository.findByNameIgnoreCase("Neurology")).thenReturn(Optional.empty());
        when(specialisationRepository.save(any(Specialisation.class))).thenAnswer(inv -> {
            Specialisation saved = inv.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        // First two calls hit DB only once (second call served from cache)
        publicCatalogService.listSpecialisations();
        publicCatalogService.listSpecialisations();
        verify(specialisationRepository, times(1)).findByActiveTrueOrderByNameAsc();

        // Mutation triggers @CacheEvict
        managerCatalogService.createSpecialisation(new SpecialisationRequest("Neurology", "Brain and nerves"));

        // Next read queries DB again
        publicCatalogService.listSpecialisations();
        verify(specialisationRepository, times(2)).findByActiveTrueOrderByNameAsc();
    }

    @Test
    void updatingDoctorStatusEvictsPublicDoctorsCache() {
        UUID doctorId = UUID.randomUUID();
        UserAccount user = new UserAccount();
        user.setId(UUID.randomUUID());
        user.setFullName("Dr. Ruwan Perera");
        user.setEmail("dr.ruwan@lankacare.lk");

        Specialisation spec = new Specialisation();
        spec.setId(UUID.randomUUID());
        spec.setName("Dermatology");

        Doctor doctor = new Doctor();
        doctor.setId(doctorId);
        doctor.setUser(user);
        doctor.setSpecialisation(spec);
        doctor.setSlmcNumber("SLMC-99887");
        doctor.setQualifications("MBBS, MD");
        doctor.setConsultationFee(new BigDecimal("2800.00"));
        doctor.setActive(true);

        when(doctorRepository.findPublicCatalog(null)).thenReturn(List.of(doctor));
        when(doctorRepository.findWithDetailsById(doctorId)).thenReturn(Optional.of(doctor));
        when(doctorRepository.save(any(Doctor.class))).thenAnswer(inv -> inv.getArgument(0));

        // First two calls hit DB only once
        publicCatalogService.listDoctors(null);
        publicCatalogService.listDoctors(null);
        verify(doctorRepository, times(1)).findPublicCatalog(null);

        // Mutation triggers @CacheEvict
        managerCatalogService.updateDoctorStatus(doctorId, false);

        // Next read queries DB again
        publicCatalogService.listDoctors(null);
        verify(doctorRepository, times(2)).findPublicCatalog(null);
    }
}