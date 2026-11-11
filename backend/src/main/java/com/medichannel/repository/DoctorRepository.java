package com.medichannel.repository;

import com.medichannel.entity.Doctor;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface DoctorRepository extends JpaRepository<Doctor, UUID> {

    @EntityGraph(attributePaths = {"user", "specialisation"})
    List<Doctor> findByActiveTrue();

    @Query("""
            SELECT d FROM Doctor d
            JOIN FETCH d.user u
            JOIN FETCH d.specialisation s
            WHERE d.active = TRUE
              AND (:specialisationId IS NULL OR s.id = :specialisationId)
            """)
    List<Doctor> findPublicCatalog(@Param("specialisationId") UUID specialisationId);
}
