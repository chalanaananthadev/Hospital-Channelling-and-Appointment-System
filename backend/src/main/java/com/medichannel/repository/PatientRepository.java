package com.medichannel.repository;

import com.medichannel.entity.Patient;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PatientRepository extends JpaRepository<Patient, UUID> {

    boolean existsByNicHmac(String nicHmac);

    Optional<Patient> findByUserId(UUID userId);
}
