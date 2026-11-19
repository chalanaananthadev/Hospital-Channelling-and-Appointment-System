package com.medichannel.repository;

import com.medichannel.entity.Doctor;
import com.medichannel.entity.UserAccount;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
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

    @EntityGraph(attributePaths = {"user", "specialisation"})
    List<Doctor> findAllByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = {"user", "specialisation"})
    Optional<Doctor> findWithDetailsById(UUID id);

    Optional<Doctor> findBySlmcNumberIgnoreCase(String slmcNumber);

    boolean existsBySlmcNumberIgnoreCaseAndIdNot(String slmcNumber, UUID id);

    boolean existsByUserId(UUID userId);

    @Query("""
            SELECT u FROM UserAccount u
            WHERE u.role = com.medichannel.enums.Role.DOCTOR
              AND u.active = TRUE
              AND NOT EXISTS (SELECT d FROM Doctor d WHERE d.user.id = u.id)
            """)
    List<UserAccount> findUnassignedDoctorUsers();
}