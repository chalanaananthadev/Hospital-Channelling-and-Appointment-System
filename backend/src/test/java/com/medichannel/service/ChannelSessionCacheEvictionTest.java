package com.medichannel.service;

import com.medichannel.config.CacheConfig;
import com.medichannel.dto.session.SessionCreateRequest;
import com.medichannel.entity.ChannelSession;
import com.medichannel.entity.Doctor;
import com.medichannel.entity.Specialisation;
import com.medichannel.entity.UserAccount;
import com.medichannel.enums.SessionStatus;
import com.medichannel.enums.SessionType;
import com.medichannel.repository.AppointmentRepository;
import com.medichannel.repository.ChannelSessionRepository;
import com.medichannel.repository.DoctorRepository;
import com.medichannel.repository.NoticeRepository;
import com.medichannel.repository.RoomRepository;
import com.medichannel.repository.SpecialisationRepository;
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
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringJUnitConfig(classes = ChannelSessionCacheEvictionTest.TestConfig.class)
@TestPropertySource(properties = "medichannel.cache.public-ttl-seconds=60")
class ChannelSessionCacheEvictionTest {

    @Configuration
    @Import({
            CacheConfig.class,
            PublicDoctorCatalogCache.class,
            PublicCatalogService.class,
            ChannelSessionService.class
    })
    static class TestConfig {
        @Bean
        static PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer() {
            return new PropertySourcesPlaceholderConfigurer();
        }

        @Bean
        ChannelSessionRepository channelSessionRepository() {
            return Mockito.mock(ChannelSessionRepository.class);
        }

        @Bean
        DoctorRepository doctorRepository() {
            return Mockito.mock(DoctorRepository.class);
        }

        @Bean
        RoomRepository roomRepository() {
            return Mockito.mock(RoomRepository.class);
        }

        @Bean
        AppointmentRepository appointmentRepository() {
            return Mockito.mock(AppointmentRepository.class);
        }

        @Bean
        AuditService auditService() {
            return Mockito.mock(AuditService.class);
        }

        @Bean
        SpecialisationRepository specialisationRepository() {
            return Mockito.mock(SpecialisationRepository.class);
        }

        @Bean
        NoticeRepository noticeRepository() {
            return Mockito.mock(NoticeRepository.class);
        }
    }

    @Autowired
    private PublicCatalogService publicCatalogService;
    @Autowired
    private ChannelSessionService sessionService;
    @Autowired
    private CacheManager cacheManager;
    @Autowired
    private DoctorRepository doctorRepository;
    @Autowired
    private ChannelSessionRepository sessionRepository;
    @Autowired
    private AppointmentRepository appointmentRepository;

    @BeforeEach
    void resetAll() {
        cacheManager.getCacheNames().forEach(name -> {
            Cache cache = cacheManager.getCache(name);
            if (cache != null) {
                cache.clear();
            }
        });
        Mockito.reset(doctorRepository, sessionRepository, appointmentRepository);
    }

    @Test
    void creatingSessionEvictsPublicDoctorsCache() {
        UserAccount user = new UserAccount();
        user.setId(UUID.randomUUID());
        user.setFullName("Dr. Nimal Silva");

        Specialisation spec = new Specialisation();
        spec.setId(UUID.randomUUID());
        spec.setName("Cardiology");

        Doctor doctor = new Doctor();
        doctor.setId(UUID.randomUUID());
        doctor.setUser(user);
        doctor.setSpecialisation(spec);
        doctor.setSlmcNumber("SLMC-12345");
        doctor.setQualifications("MBBS, MD");
        doctor.setConsultationFee(new BigDecimal("3500.00"));
        doctor.setActive(true);

        when(doctorRepository.findPublicCatalog(null)).thenReturn(List.of(doctor));
        when(doctorRepository.findWithDetailsById(doctor.getId())).thenReturn(Optional.of(doctor));
        when(sessionRepository.countDoctorOverlaps(any(), any(), any(), any(), any())).thenReturn(0L);
        when(sessionRepository.save(any(ChannelSession.class))).thenAnswer(inv -> {
            ChannelSession s = inv.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });
        when(appointmentRepository.countActiveBySessionId(any())).thenReturn(0L);
        when(appointmentRepository.findMaxNumberBySessionId(any())).thenReturn(0);

        publicCatalogService.listDoctors(null);
        publicCatalogService.listDoctors(null);
        verify(doctorRepository, times(1)).findPublicCatalog(null);

        sessionService.createSession(new SessionCreateRequest(
                doctor.getId(),
                null,
                LocalDate.now(ChannelSessionService.COLOMBO_ZONE).plusDays(2),
                LocalTime.of(17, 0),
                LocalTime.of(19, 0),
                SessionType.ONLINE,
                15,
                10,
                new BigDecimal("3000.00"),
                SessionStatus.OPEN
        ));

        publicCatalogService.listDoctors(null);
        verify(doctorRepository, times(2)).findPublicCatalog(null);
    }
}