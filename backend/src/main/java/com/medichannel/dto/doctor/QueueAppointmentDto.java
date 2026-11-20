package com.medichannel.dto.doctor;

import com.medichannel.enums.AppointmentStatus;
import com.medichannel.enums.PaymentStatus;
import com.medichannel.enums.SessionType;

import java.time.LocalTime;
import java.util.UUID;

public record QueueAppointmentDto(
        UUID appointmentId,
        int appointmentNumber,
        String bookingReference,
        UUID patientId,
        String patientName,
        SessionType appointmentType,
        AppointmentStatus status,
        PaymentStatus paymentStatus,
        LocalTime estimatedTime,
        String reasonForVisit,
        boolean hasConsultation,
        boolean consultationReadOnly
) {}