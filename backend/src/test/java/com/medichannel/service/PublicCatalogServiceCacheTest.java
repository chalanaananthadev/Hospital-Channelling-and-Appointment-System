package com.medichannel.service;

import com.medichannel.config.CacheConfig;
import com.medichannel.dto.catalog.PublicDoctorDto;
import com.medichannel.entity.Doctor;
import com.medichannel.entity.Specialisation;
import com.medichannel.entity.UserAccount;
import com.medichannel.repository.DoctorRepository;
import com.medichannel.repository.NoticeRepository;
import com.medichannel.repository.SpecialisationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = {
        CacheConfig.class,
        PublicDoctorCatalogCache.class,
        PublicCatalogService.class
})
@TestPropertySource(properties = {
        "medichannel.cache.public-ttl-seconds=120"
})
class PublicCatalogServiceCacheTest {

    @MockBean
    private DoctorRepository doctorRepository;

    @MockBean
    private SpecialisationRepository specialisationRepository;

    @MockBean
    private NoticeRepository noticeRepository;

    @Autowired
    private PublicCatalogService publicCatalogService;

    private Doctor buildMockDoctor(UUID id, String fullName, UUID specId, String specName) {
        UserAccount user = new UserAccount();
        user.setId(UUID.randomUUID());
        user.setFullName(fullName);

        Specialisation spec = new Specialisation();
        spec.setId(specId);
        spec.setName(specName);

        Doctor doctor = new Doctor();
        doctor.setId(id);
        doctor.setUser(user);
        doctor.setSpecialisation(spec);
        doctor.setQualifications("MBBS, MD");
        doctor.setConsultationFee(new BigDecimal("3000.00"));
        doctor.setBio("Consultant specialist");
        return doctor;
    }

    @Test
    @DisplayName("searchDoctors caches query, uses separate keys per specialisation, and honors eviction")
    void testSearchDoctorsCachingAndEviction() {
        UUID specCardiology = UUID.randomUUID();
        UUID specDermatology = UUID.randomUUID();

        Doctor doctor1 = buildMockDoctor(UUID.randomUUID(), "Dr. Kavindu Perera", specCardiology, "Cardiology");
        Doctor doctor2 = buildMockDoctor(UUID.randomUUID(), "Dr. Nimali Silva", specDermatology, "Dermatology");

        when(doctorRepository.findPublicCatalog(specCardiology)).thenReturn(List.of(doctor1));
        when(doctorRepository.findPublicCatalog(specDermatology)).thenReturn(List.of(doctor2));

        // 1. First call for Cardiology - repository should be invoked
        List<PublicDoctorDto> result1 = publicCatalogService.searchDoctors(specCardiology, null);
        assertEquals(1, result1.size());
        assertEquals("Dr. Kavindu Perera", result1.get(0).fullName());
        verify(doctorRepository, times(1)).findPublicCatalog(specCardiology);

        // 2. Second call for Cardiology with filter - cache hit, repository NOT invoked again
        List<PublicDoctorDto> result2 = publicCatalogService.searchDoctors(specCardiology, "Kavindu");
        assertEquals(1, result2.size());
        assertEquals("Dr. Kavindu Perera", result2.get(0).fullName());
        verify(doctorRepository, times(1)).findPublicCatalog(specCardiology);

        // 3. Call for Dermatology - different key, repository invoked for Dermatology
        List<PublicDoctorDto> result3 = publicCatalogService.searchDoctors(specDermatology, null);
        assertEquals(1, result3.size());
        assertEquals("Dr. Nimali Silva", result3.get(0).fullName());
        verify(doctorRepository, times(1)).findPublicCatalog(specDermatology);

        // 4. Evict public catalog cache
        publicCatalogService.evictPublicCatalog();

        // 5. Subsequent call for Cardiology after eviction - cache miss, repository invoked 2nd time
        List<PublicDoctorDto> result4 = publicCatalogService.searchDoctors(specCardiology, null);
        assertEquals(1, result4.size());
        verify(doctorRepository, times(2)).findPublicCatalog(specCardiology);
    }
}
