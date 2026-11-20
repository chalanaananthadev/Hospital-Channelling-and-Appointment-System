package com.medichannel.dto.doctor;

import java.util.List;

public record DoctorSessionQueueDto(
        DoctorSessionDto session,
        List<QueueAppointmentDto> queue
) {}