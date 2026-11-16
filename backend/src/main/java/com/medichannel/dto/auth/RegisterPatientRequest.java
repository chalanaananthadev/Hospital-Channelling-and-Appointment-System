package com.medichannel.dto.auth;

import com.medichannel.enums.Gender;
import com.medichannel.validation.SriLankanNic;
import com.medichannel.validation.SriLankanPhone;
import com.medichannel.validation.StrongPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record RegisterPatientRequest(
        @NotBlank @Size(max = 120) String fullName,
        @NotBlank @Email String email,
        @SriLankanNic String nic,
        @NotNull @Past LocalDate dateOfBirth,
        @NotNull Gender gender,
        @SriLankanPhone String phone,
        @NotBlank @Size(max = 250) String address,
        @StrongPassword @Size(max = 72) String password
) {
}
