package com.medichannel.entity;

import com.medichannel.enums.Gender;
import com.medichannel.security.AesGcmAttributeConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "patients")
public class Patient {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private UserAccount user;

    @Convert(converter = AesGcmAttributeConverter.class)
    @Column(name = "nic_encrypted", nullable = false)
    private String nic;

    @Column(name = "nic_hmac", nullable = false, unique = true, length = 64)
    private String nicHmac;

    @Convert(converter = AesGcmAttributeConverter.class)
    @Column(name = "date_of_birth_encrypted", nullable = false)
    private String dateOfBirth;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Gender gender;

    @Convert(converter = AesGcmAttributeConverter.class)
    @Column(name = "phone_encrypted", nullable = false)
    private String phone;

    @Convert(converter = AesGcmAttributeConverter.class)
    @Column(name = "address_encrypted", nullable = false)
    private String address;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
