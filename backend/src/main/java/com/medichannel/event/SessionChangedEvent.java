package com.medichannel.event;

import com.medichannel.enums.SessionStatus;

import java.util.List;
import java.util.UUID;

public record SessionChangedEvent(
        UUID sessionId,
        ChangeType changeType,
        SessionStatus status,
        int delayMinutes,
        int numbersLeft,
        List<UUID> affectedPatientUserIds
) {
    public enum ChangeType {
        CREATED,
        UPDATED,
        DELAYED,
        CANCELLED,
        STATUS_CHANGED
    }
}