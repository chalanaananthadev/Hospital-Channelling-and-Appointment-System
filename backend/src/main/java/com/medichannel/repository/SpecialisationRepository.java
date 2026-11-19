package com.medichannel.repository;

import com.medichannel.entity.Specialisation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SpecialisationRepository extends JpaRepository<Specialisation, UUID> {

    List<Specialisation> findByActiveTrueOrderByNameAsc();

    Optional<Specialisation> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);

    Page<Specialisation> findByNameContainingIgnoreCase(String name, Pageable pageable);
}