package com.medichannel.repository;

import com.medichannel.entity.AppNotification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AppNotificationRepository extends JpaRepository<AppNotification, UUID> {
}
