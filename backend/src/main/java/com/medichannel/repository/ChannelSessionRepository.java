package com.medichannel.repository;

import com.medichannel.entity.ChannelSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ChannelSessionRepository extends JpaRepository<ChannelSession, UUID> {

    /**
     * Used by Member 3 (Booking) to serialize concurrent bookings and enforce BR-03 (max_patients cap) under row-level lock.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ChannelSession s where s.id = :id")
    Optional<ChannelSession> findByIdForUpdate(@Param("id") UUID id);
}
