package com.medichannel.event;

import com.medichannel.enums.AppointmentStatus;
import com.medichannel.enums.SessionStatus;
import com.medichannel.enums.SessionType;

import java.util.UUID;

public record QueueUpdatedEvent(
        UUID sessionId,
        ActionType actionType,
        SessionStatus sessionStatus,
        SessionType sessionType,
        Integer nowServing,
        UUID appointmentId,
        AppointmentStatus appointmentStatus,
        UUID calledPatientUserId,
        UUID threeAwayPatientUserId
) {
    public enum ActionType {
        SESSION_STARTED,
        PATIENT_CALLED,
        PATIENT_COMPLETED,
        PATIENT_NO_SHOW,
        SESSION_ENDED
    }
}