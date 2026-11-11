package com.medichannel.service;

import com.medichannel.dto.catalog.NoticeDto;
import com.medichannel.dto.catalog.PublicDoctorDto;
import com.medichannel.dto.catalog.SpecialisationDto;
import com.medichannel.repository.DoctorRepository;
import com.medichannel.repository.NoticeRepository;
import com.medichannel.repository.SpecialisationRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class PublicCatalogService {

    private final SpecialisationRepository specialisationRepository;
    private final DoctorRepository doctorRepository;
    private final NoticeRepository noticeRepository;

    public PublicCatalogService(
            SpecialisationRepository specialisationRepository,
            DoctorRepository doctorRepository,
            NoticeRepository noticeRepository
    ) {
        this.specialisationRepository = specialisationRepository;
        this.doctorRepository = doctorRepository;
        this.noticeRepository = noticeRepository;
    }

    @Cacheable("publicSpecialisations")
    @Transactional(readOnly = true)
    public List<SpecialisationDto> listSpecialisations() {
        return specialisationRepository.findByActiveTrueOrderByNameAsc().stream()
                .map(item -> new SpecialisationDto(item.getId(), item.getName(), item.getDescription()))
                .toList();
    }

    @Cacheable(value = "publicDoctors", key = "#specialisationId ?: 'all'")
    @Transactional(readOnly = true)
    public List<PublicDoctorDto> listDoctors(UUID specialisationId) {
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

    public List<PublicDoctorDto> searchDoctors(UUID specialisationId, String name) {
        List<PublicDoctorDto> doctors = listDoctors(specialisationId);
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

    @CacheEvict(value = {"publicDoctors", "publicSpecialisations"}, allEntries = true)
    public void evictPublicCatalog() {
        // Called by doctor/session services when catalog data changes (Member 2).
    }
}
