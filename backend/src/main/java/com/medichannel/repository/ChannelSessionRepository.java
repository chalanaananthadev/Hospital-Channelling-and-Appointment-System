package com.medichannel.repository;

import com.medichannel.entity.ChannelSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChannelSessionRepository extends JpaRepository<ChannelSession, UUID> {

    /**
     * Used by Member 3 (Booking) to serialize concurrent bookings and enforce BR-03 (max_patients cap) under row-level lock.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ChannelSession s where s.id = :id")
    Optional<ChannelSession> findByIdForUpdate(@Param("id") UUID id);

    @EntityGraph(attributePaths = {"doctor", "doctor.user", "doctor.specialisation", "room"})
    Optional<ChannelSession> findWithDetailsById(UUID id);

    @EntityGraph(attributePaths = {"doctor", "doctor.user", "doctor.specialisation", "room"})
    List<ChannelSession> findAllByOrderBySessionDateDescStartTimeAsc();

    @EntityGraph(attributePaths = {"doctor", "doctor.user", "doctor.specialisation", "room"})
    List<ChannelSession> findBySessionDateGreaterThanEqualOrderBySessionDateAscStartTimeAsc(LocalDate fromDate);

    @EntityGraph(attributePaths = {"doctor", "doctor.user", "doctor.specialisation", "room"})
    List<ChannelSession> findByDoctorIdOrderBySessionDateDescStartTimeAsc(UUID doctorId);

    @EntityGraph(attributePaths = {"doctor", "doctor.user", "doctor.specialisation", "room"})
    List<ChannelSession> findByDoctorIdAndSessionDateOrderByStartTimeAsc(UUID doctorId, LocalDate sessionDate);

    /**
     * BR-02: Checks whether the doctor already has a non-cancelled overlapping session on the same date.
     * Back-to-back sessions (end == next start) are allowed.
     */
    @Query("""
            SELECT COUNT(s) FROM ChannelSession s
            WHERE s.doctor.id = :doctorId
              AND s.sessionDate = :sessionDate
              AND s.status <> com.medichannel.enums.SessionStatus.CANCELLED
              AND (:excludeId IS NULL OR s.id <> :excludeId)
              AND s.startTime < :endTime
              AND s.endTime > :startTime
            """)
    long countDoctorOverlaps(
            @Param("doctorId") UUID doctorId,
            @Param("sessionDate") LocalDate sessionDate,
            @Param("startTime") LocalTime startTime,
            @Param("endTime") LocalTime endTime,
            @Param("excludeId") UUID excludeId
    );

    /**
     * BR-02: Checks whether the room is already booked by a non-cancelled overlapping session on the same date.
     */
    @Query("""
            SELECT COUNT(s) FROM ChannelSession s
            WHERE s.room.id = :roomId
              AND s.sessionDate = :sessionDate
              AND s.status <> com.medichannel.enums.SessionStatus.CANCELLED
              AND (:excludeId IS NULL OR s.id <> :excludeId)
              AND s.startTime < :endTime
              AND s.endTime > :startTime
            """)
    long countRoomOverlaps(
            @Param("roomId") UUID roomId,
            @Param("sessionDate") LocalDate sessionDate,
            @Param("startTime") LocalTime startTime,
            @Param("endTime") LocalTime endTime,
            @Param("excludeId") UUID excludeId
    );
}