# MediChannel — Entity Relationship Diagram (ERD) & Schema Specifications

This document describes the complete PostgreSQL relational schema (13 tables) conforming strictly to assignment requirements, Sri Lankan medical regulation rules (SLMC), BR-01 to BR-11, and medical privacy boundaries.

---

## 1. Mermaid Entity-Relationship Diagram

```mermaid
erDiagram
    users ||--o| patients : "has profile"
    users ||--o| doctors : "has doctor profile"
    users ||--o{ audit_logs : "triggers"
    users ||--o{ notices : "authors"
    users ||--o{ notifications : "receives"
    specialisations ||--o{ doctors : "categorises"
    rooms ||--o{ sessions : "hosts (physical)"
    doctors ||--o{ sessions : "conducts"
    sessions ||--o{ appointments : "contains"
    patients ||--o{ appointments : "books"
    appointments ||--o| consultations : "proceeds to"
    doctors ||--o{ consultations : "examines"
    consultations ||--o{ chat_messages : "contains"
    users ||--o{ chat_messages : "sends"
    appointments ||--o| feedback : "receives"
    doctors ||--o{ feedback : "evaluated in"
    patients ||--o{ feedback : "submits"

    users {
        UUID id PK
        TEXT email_encrypted
        VARCHAR email_hmac UK "HMAC-SHA256"
        VARCHAR password_hash "BCrypt"
        VARCHAR role "CUSTOMER, DOCTOR, MANAGER, ADMIN"
        TEXT full_name_encrypted "AES-256-GCM"
        BOOLEAN is_active
        BOOLEAN must_change_password
        INTEGER failed_login_attempts "Lockout counter"
        TIMESTAMPTZ locked_until "15-min lockout"
        TIMESTAMPTZ last_login_at
        TIMESTAMPTZ created_at
        TIMESTAMPTZ updated_at
    }

    patients {
        UUID id PK
        UUID user_id FK, UK
        TEXT nic_encrypted "AES-256-GCM"
        VARCHAR nic_hmac UK "HMAC-SHA256"
        TEXT date_of_birth_encrypted "AES-256-GCM"
        VARCHAR gender "MALE, FEMALE, OTHER"
        TEXT phone_encrypted "AES-256-GCM"
        TEXT address_encrypted "AES-256-GCM"
        TIMESTAMPTZ created_at
        TIMESTAMPTZ updated_at
    }

    specialisations {
        UUID id PK
        VARCHAR name UK
        TEXT description
        BOOLEAN is_active
        TIMESTAMPTZ created_at
        TIMESTAMPTZ updated_at
    }

    doctors {
        UUID id PK
        UUID user_id FK, UK
        UUID specialisation_id FK
        VARCHAR slmc_number UK
        TEXT qualifications
        NUMERIC consultation_fee
        TEXT bio
        BOOLEAN is_active
        TIMESTAMPTZ created_at
        TIMESTAMPTZ updated_at
    }

    rooms {
        UUID id PK
        VARCHAR name UK
        VARCHAR location
        BOOLEAN is_active
        TIMESTAMPTZ created_at
        TIMESTAMPTZ updated_at
    }

    sessions {
        UUID id PK
        UUID doctor_id FK
        UUID room_id FK "Null if ONLINE"
        DATE session_date
        TIME start_time
        TIME end_time
        VARCHAR session_type "PHYSICAL, ONLINE"
        INTEGER max_patients "1-60"
        INTEGER minutes_per_patient "5-30"
        NUMERIC fee
        INTEGER delay_minutes
        VARCHAR status "SCHEDULED, OPEN, IN_PROGRESS, COMPLETED, CANCELLED"
        INTEGER now_serving
        TIMESTAMPTZ created_at
        TIMESTAMPTZ updated_at
    }

    appointments {
        UUID id PK
        UUID session_id FK
        UUID patient_id FK
        INTEGER appointment_number "Sequential, unique per session"
        VARCHAR appointment_type "PHYSICAL, ONLINE"
        TEXT reason_encrypted "AES-256-GCM"
        VARCHAR status "BOOKED, ARRIVED, IN_CONSULTATION, COMPLETED, NO_SHOW, CANCELLED"
        VARCHAR payment_status "PENDING, PAID"
        VARCHAR payment_method "CASH, CARD, NULL"
        TIME estimated_time
        VARCHAR booking_reference UK
        TIMESTAMPTZ cancelled_at
        TIMESTAMPTZ created_at
        TIMESTAMPTZ updated_at
    }

    consultations {
        UUID id PK
        UUID appointment_id FK, UK
        UUID doctor_id FK
        TEXT diagnosis_encrypted "AES-256-GCM"
        TEXT advice_encrypted "AES-256-GCM"
        TEXT prescription_encrypted "AES-256-GCM"
        BOOLEAN is_read_only "True once finished"
        TIMESTAMPTZ started_at
        TIMESTAMPTZ ended_at
        TIMESTAMPTZ created_at
        TIMESTAMPTZ updated_at
    }

    chat_messages {
        UUID id PK
        UUID consultation_id FK
        UUID sender_user_id FK
        TEXT body_encrypted "AES-256-GCM"
        TIMESTAMPTZ created_at
    }

    notifications {
        UUID id PK
        UUID user_id FK
        VARCHAR notification_type "CALLED, THREE_AWAY, SESSION_DELAYED, SESSION_CANCELLED, CONSULTATION_STARTED"
        VARCHAR title
        TEXT body
        BOOLEAN is_read
        UUID session_id FK
        UUID appointment_id FK
        TIMESTAMPTZ created_at
    }

    feedback {
        UUID id PK
        UUID appointment_id FK, UK
        UUID patient_id FK
        UUID doctor_id FK
        INTEGER rating "1 to 5"
        TEXT comment
        VARCHAR status "PENDING, APPROVED, HIDDEN, DELETED"
        TIMESTAMPTZ created_at
        TIMESTAMPTZ updated_at
    }

    notices {
        UUID id PK
        VARCHAR title
        TEXT body
        BOOLEAN is_published
        TIMESTAMPTZ published_at
        UUID created_by FK
        TIMESTAMPTZ created_at
        TIMESTAMPTZ updated_at
    }

    audit_logs {
        UUID id PK
        UUID user_id FK
        VARCHAR action "LOGIN_SUCCESS, LOGIN_FAILURE, ACCOUNT_LOCKED, etc."
        VARCHAR entity_type
        UUID entity_id
        VARCHAR ip_address
        TIMESTAMPTZ created_at
    }
```

---

## 2. Integrity and Business Constraints Summary

| Table | Constraint | Purpose / Rule |
| :--- | :--- | :--- |
| `users` | `uq_users_email_hmac` | Deterministic HMAC-SHA256 uniqueness without decrypting email |
| `users` | `chk_users_role` | Enforces 4 valid roles: `CUSTOMER`, `DOCTOR`, `MANAGER`, `ADMIN` |
| `patients` | `uq_patients_nic_hmac` | Uniqueness of Sri Lankan National Identity Card |
| `sessions` | `chk_sessions_physical_room` | `PHYSICAL` requires a room; `ONLINE` must have null room (BR-01) |
| `sessions` | `chk_sessions_max_patients` | Limit 1 to 60 patients per session |
| `sessions` | `chk_sessions_minutes` | Slot duration 5 to 30 minutes |
| `appointments` | `uq_appointments_number` | Unique slot number per session: no duplicates (BR-03) |
| `appointments` | `uq_appointments_active_patient` | Partial unique index: only 1 active booking per patient per session (BR-04) |
| `audit_logs` | `trg_audit_logs_no_update` | PostgreSQL row-level BEFORE UPDATE/DELETE trigger raising 'audit_logs is immutable' |
| `audit_logs` | `trg_audit_logs_no_truncate` | PostgreSQL statement-level BEFORE TRUNCATE trigger raising 'audit_logs is immutable' |
| `audit_logs` | `REVOKE UPDATE, DELETE, TRUNCATE` | SQL privilege restriction preventing modifications from public/standard roles |

---

## 3. Audit Log Immutability Enforcement (Defense-in-Depth)

The `audit_logs` table represents a non-repudiable record of system events. Immutability is strictly enforced across three architectural layers:

1. **Database Trigger Layer**:
   - `prevent_audit_log_change()` trigger function executed `BEFORE UPDATE OR DELETE ON audit_logs FOR EACH ROW`.
   - Statement-level trigger `BEFORE TRUNCATE ON audit_logs FOR EACH STATEMENT`.
   - Raises PL/pgSQL exception: `'audit_logs is immutable'`.
2. **Database Permissions Layer**:
   - Explicit `REVOKE UPDATE, DELETE, TRUNCATE ON audit_logs FROM PUBLIC;` preventing standard database users from altering the table.
3. **ORM / Application Layer**:
   - Hibernate `@org.hibernate.annotations.Immutable` annotation on the `AuditLog` entity.
   - All `@Column` and `@JoinColumn` attributes mapped with `updatable = false`.
   - `AuditService.record()` is append-only; no modification or deletion methods exist in the service or repository.


