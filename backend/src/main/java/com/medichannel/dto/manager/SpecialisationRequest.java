package com.medichannel.dto.manager;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SpecialisationRequest(
        @NotBlank(message = "Specialisation name is required")
        @Size(max = 80, message = "Specialisation name must be at most 80 characters")
        String name,

        @Size(max = 1000, message = "Description must be at most 1000 characters")
        String description
) {}