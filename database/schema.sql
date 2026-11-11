-- MediChannel PostgreSQL schema
-- Time zone: Asia/Colombo. Money: NUMERIC. Historical rows are never deleted (BR-11).
-- Encrypted columns store AES-256-GCM ciphertext (random IV per value).
-- email_hmac / nic_hmac store HMAC-SHA256 of the normalised plaintext for lookup.

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE users (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email_encrypted         TEXT NOT NULL,
    email_hmac              VARCHAR(64) NOT NULL,
    password_hash           VARCHAR(100) NOT NULL,
    role                    VARCHAR(20) NOT NULL,
    full_name_encrypted     TEXT NOT NULL,
    is_active               BOOLEAN NOT NULL DEFAULT TRUE,
    must_change_password    BOOLEAN NOT NULL DEFAULT FALSE,
    failed_login_attempts   INTEGER NOT NULL DEFAULT 0,
    locked_until            TIMESTAMPTZ,
    last_login_at           TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_users_role CHECK (role IN ('CUSTOMER', 'DOCTOR', 'MANAGER', 'ADMIN')),
    CONSTRAINT chk_users_failed_attempts CHECK (failed_login_attempts >= 0),
    CONSTRAINT uq_users_email_hmac UNIQUE (email_hmac)
);

CREATE TABLE patients (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID NOT NULL REFERENCES users (id),
    nic_encrypted       TEXT NOT NULL,
    nic_hmac            VARCHAR(64) NOT NULL,
    date_of_birth_encrypted TEXT NOT NULL,
    gender              VARCHAR(20) NOT NULL,
    phone_encrypted     TEXT NOT NULL,
    address_encrypted   TEXT NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_patients_user UNIQUE (user_id),
    CONSTRAINT uq_patients_nic_hmac UNIQUE (nic_hmac),
    CONSTRAINT chk_patients_gender CHECK (gender IN ('MALE', 'FEMALE', 'OTHER'))
);

CREATE TABLE specialisations (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(80) NOT NULL,
    description     TEXT,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_specialisations_name UNIQUE (name)
);

CREATE TABLE doctors (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                 UUID NOT NULL REFERENCES users (id),
    specialisation_id       UUID NOT NULL REFERENCES specialisations (id),
    slmc_number             VARCHAR(30) NOT NULL,
    qualifications          TEXT NOT NULL,
    consultation_fee        NUMERIC(12, 2) NOT NULL,
    bio                     TEXT,
    is_active               BOOLEAN NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_doctors_user UNIQUE (user_id),
    CONSTRAINT uq_doctors_slmc UNIQUE (slmc_number),
    CONSTRAINT chk_doctors_fee CHECK (consultation_fee >= 0)
);

CREATE TABLE rooms (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(40) NOT NULL,
    location        VARCHAR(120),
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_rooms_name UNIQUE (name)
);

CREATE TABLE sessions (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    doctor_id               UUID NOT NULL REFERENCES doctors (id),
    room_id                 UUID REFERENCES rooms (id),
    session_date            DATE NOT NULL,
    start_time              TIME NOT NULL,
    end_time                TIME NOT NULL,
    session_type            VARCHAR(20) NOT NULL,
    max_patients            INTEGER NOT NULL,
    minutes_per_patient     INTEGER NOT NULL,
    fee                     NUMERIC(12, 2) NOT NULL,
    delay_minutes           INTEGER NOT NULL DEFAULT 0,
    status                  VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
    now_serving             INTEGER,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_sessions_type CHECK (session_type IN ('PHYSICAL', 'ONLINE')),
    CONSTRAINT chk_sessions_status CHECK (status IN ('SCHEDULED', 'OPEN', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT chk_sessions_max_patients CHECK (max_patients BETWEEN 1 AND 60),
    CONSTRAINT chk_sessions_minutes CHECK (minutes_per_patient BETWEEN 5 AND 30),
    CONSTRAINT chk_sessions_fee CHECK (fee >= 0),
    CONSTRAINT chk_sessions_delay CHECK (delay_minutes >= 0),
    CONSTRAINT chk_sessions_time CHECK (end_time > start_time),
    CONSTRAINT chk_sessions_physical_room CHECK (
        (session_type = 'PHYSICAL' AND room_id IS NOT NULL)
        OR (session_type = 'ONLINE' AND room_id IS NULL)
    )
);

CREATE TABLE appointments (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id              UUID NOT NULL REFERENCES sessions (id),
    patient_id              UUID NOT NULL REFERENCES patients (id),
    appointment_number      INTEGER NOT NULL,
    appointment_type        VARCHAR(20) NOT NULL,
    reason_encrypted        TEXT NOT NULL,
    status                  VARCHAR(30) NOT NULL DEFAULT 'BOOKED',
    payment_status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    payment_method          VARCHAR(20),
    estimated_time          TIME NOT NULL,
    booking_reference       VARCHAR(20) NOT NULL,
    cancelled_at            TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_appointments_number UNIQUE (session_id, appointment_number),
    CONSTRAINT uq_appointments_reference UNIQUE (booking_reference),
    CONSTRAINT chk_appointments_number CHECK (appointment_number >= 1),
    CONSTRAINT chk_appointments_type CHECK (appointment_type IN ('PHYSICAL', 'ONLINE')),
    CONSTRAINT chk_appointments_status CHECK (status IN (
        'BOOKED', 'ARRIVED', 'IN_CONSULTATION', 'COMPLETED', 'NO_SHOW', 'CANCELLED'
    )),
    CONSTRAINT chk_appointments_payment_status CHECK (payment_status IN ('PENDING', 'PAID')),
    CONSTRAINT chk_appointments_payment_method CHECK (
        payment_method IS NULL OR payment_method IN ('CASH', 'CARD')
    )
);

-- BR-04: one active appointment per patient per session (history rows may remain)
CREATE UNIQUE INDEX uq_appointments_active_patient
    ON appointments (session_id, patient_id)
    WHERE status IN ('BOOKED', 'ARRIVED', 'IN_CONSULTATION');

CREATE TABLE consultations (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    appointment_id              UUID NOT NULL REFERENCES appointments (id),
    doctor_id                   UUID NOT NULL REFERENCES doctors (id),
    diagnosis_encrypted         TEXT,
    advice_encrypted            TEXT,
    prescription_encrypted      TEXT,
    is_read_only                BOOLEAN NOT NULL DEFAULT FALSE,
    started_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ended_at                    TIMESTAMPTZ,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_consultations_appointment UNIQUE (appointment_id)
);

CREATE TABLE chat_messages (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    consultation_id         UUID NOT NULL REFERENCES consultations (id),
    sender_user_id          UUID NOT NULL REFERENCES users (id),
    body_encrypted          TEXT NOT NULL,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_chat_body_not_blank CHECK (length(body_encrypted) > 0)
);

CREATE TABLE notifications (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                 UUID NOT NULL REFERENCES users (id),
    notification_type       VARCHAR(40) NOT NULL,
    title                   VARCHAR(160) NOT NULL,
    body                    TEXT NOT NULL,
    is_read                 BOOLEAN NOT NULL DEFAULT FALSE,
    session_id              UUID REFERENCES sessions (id),
    appointment_id          UUID REFERENCES appointments (id),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_notifications_type CHECK (notification_type IN (
        'CALLED', 'THREE_AWAY', 'SESSION_DELAYED', 'SESSION_CANCELLED', 'CONSULTATION_STARTED'
    ))
);

CREATE TABLE feedback (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    appointment_id          UUID NOT NULL REFERENCES appointments (id),
    patient_id              UUID NOT NULL REFERENCES patients (id),
    doctor_id               UUID NOT NULL REFERENCES doctors (id),
    rating                  INTEGER NOT NULL,
    comment                 TEXT,
    status                  VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_feedback_appointment UNIQUE (appointment_id),
    CONSTRAINT chk_feedback_rating CHECK (rating BETWEEN 1 AND 5),
    CONSTRAINT chk_feedback_status CHECK (status IN ('PENDING', 'APPROVED', 'HIDDEN', 'DELETED'))
);

CREATE TABLE notices (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title                   VARCHAR(160) NOT NULL,
    body                    TEXT NOT NULL,
    is_published            BOOLEAN NOT NULL DEFAULT FALSE,
    published_at            TIMESTAMPTZ,
    created_by              UUID NOT NULL REFERENCES users (id),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Immutable operational log. Never store medical plaintext here.
CREATE TABLE audit_logs (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                 UUID REFERENCES users (id),
    action                  VARCHAR(80) NOT NULL,
    entity_type             VARCHAR(40) NOT NULL,
    entity_id               UUID,
    ip_address              VARCHAR(64),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_sessions_doctor_date ON sessions (doctor_id, session_date);
CREATE INDEX idx_sessions_room_date ON sessions (room_id, session_date);
CREATE INDEX idx_appointments_session_status ON appointments (session_id, status);
CREATE INDEX idx_notifications_user_created ON notifications (user_id, created_at DESC);
CREATE INDEX idx_audit_logs_created ON audit_logs (created_at DESC);
CREATE INDEX idx_chat_messages_consultation ON chat_messages (consultation_id, created_at);

REVOKE UPDATE, DELETE ON audit_logs FROM PUBLIC;
