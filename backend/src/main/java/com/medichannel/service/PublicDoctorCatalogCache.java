package com.medichannel.service;

import com.medichannel.dto.catalog.PublicDoctorDto;
import com.medichannel.repository.DoctorRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Spring component managing cached public doctor catalog queries.
 * Separated from {@link PublicCatalogService} so that method invocations
 * pass through Spring's AOP proxy and properly trigger @Cacheable and @CacheEvict.
 */
@Component
public class PublicDoctorCatalogCache {

    private final DoctorRepository doctorRepository;

    public PublicDoctorCatalogCache(DoctorRepository doctorRepository) {
        this.doctorRepository = doctorRepository;
    }

    /**
     * Retrieves doctors for a given specialisation, caching the result in "publicDoctors".
     *
     * @param specialisationId specialisation ID, or null for all doctors
     * @return list of public doctor representations
     */
    @Cacheable(value = "publicDoctors", key = "#specialisationId ?: 'all'")
    @Transactional(readOnly = true)
    public List<PublicDoctorDto> getPublicDoctors(UUID specialisationId) {
        return doctorRepository.findPublicCatalog(specialisationId).stream()
                .map(doctor -> new PublicDoctorDto(
                        doctor.getId(),
                        doctor.getUser().getFullName(),
                        doctor.getSpecialisation().getName(),
                        doctor.getSpecialisation().getId(),
                        doctor.getQualifications(),
                        doctor.getConsultationFee(),
                        doctor.getBio()
                ))
                .toList();
    }

    /**
     * Evicts both "publicDoctors" and "publicSpecialisations" caches.
     * Must be invoked from outside this bean so Spring's AOP proxy intercepts the call.
     */
    @CacheEvict(value = {"publicDoctors", "publicSpecialisations"}, allEntries = true)
    public void evictPublicCatalog() {
        // Cache eviction interceptor handled by Spring Cache AOP proxy
    }
}
