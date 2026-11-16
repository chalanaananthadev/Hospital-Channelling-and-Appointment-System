package com.medichannel.service;

import com.medichannel.dto.catalog.NoticeDto;
import com.medichannel.dto.catalog.PublicDoctorDto;
import com.medichannel.dto.catalog.SpecialisationDto;
import com.medichannel.repository.NoticeRepository;
import com.medichannel.repository.SpecialisationRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class PublicCatalogService {

    private final SpecialisationRepository specialisationRepository;
    private final NoticeRepository noticeRepository;
    private final PublicDoctorCatalogCache doctorCatalogCache;

    public PublicCatalogService(
            SpecialisationRepository specialisationRepository,
            NoticeRepository noticeRepository,
            PublicDoctorCatalogCache doctorCatalogCache
    ) {
        this.specialisationRepository = specialisationRepository;
        this.noticeRepository = noticeRepository;
        this.doctorCatalogCache = doctorCatalogCache;
    }

    @Cacheable("publicSpecialisations")
    @Transactional(readOnly = true)
    public List<SpecialisationDto> listSpecialisations() {
        return specialisationRepository.findByActiveTrueOrderByNameAsc().stream()
                .map(item -> new SpecialisationDto(item.getId(), item.getName(), item.getDescription()))
                .toList();
    }

    public List<PublicDoctorDto> listDoctors(UUID specialisationId) {
        return doctorCatalogCache.getPublicDoctors(specialisationId);
    }

    public List<PublicDoctorDto> searchDoctors(UUID specialisationId, String name) {
        List<PublicDoctorDto> doctors = doctorCatalogCache.getPublicDoctors(specialisationId);
        if (name == null || name.isBlank()) {
            return doctors;
        }
        String needle = name.trim().toLowerCase(Locale.ROOT);
        return doctors.stream()
                .filter(doctor -> doctor.fullName().toLowerCase(Locale.ROOT).contains(needle))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<NoticeDto> listNotices() {
        return noticeRepository.findByPublishedTrueOrderByPublishedAtDesc().stream()
                .map(notice -> new NoticeDto(notice.getId(), notice.getTitle(), notice.getBody(), notice.getPublishedAt()))
                .toList();
    }

    /**
     * Evicts cached doctor and specialisation catalog data.
     * Callable by other beans (e.g. Member 2 doctor/session services).
     * Delegates to {@link PublicDoctorCatalogCache#evictPublicCatalog()} across bean boundary
     * so Spring's AOP cache proxy processes the @CacheEvict annotation.
     */
    public void evictPublicCatalog() {
        doctorCatalogCache.evictPublicCatalog();
    }
}
