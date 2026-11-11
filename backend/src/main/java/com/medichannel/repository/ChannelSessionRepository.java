package com.medichannel.repository;

import com.medichannel.entity.ChannelSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ChannelSessionRepository extends JpaRepository<ChannelSession, UUID> {
}
