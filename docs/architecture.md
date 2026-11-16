# MediChannel — Monorepo Architecture & Security Reference

## 1. System Architecture & Boundaries

```
Hospital Channelling and Appointment System/
├── backend/            # Spring Boot 3.4.5 (Java 21) REST API & STOMP WebSockets (Port 8080)
├── frontend/           # Next.js 15 (App Router, TypeScript) (Port 3000)
├── database/           # PostgreSQL 16+ Schema, Constraints & Seed Migration Scripts
└── docs/               # System documentation, security evidence & verification checklists
    ├── architecture.md
    ├── erd.md
    ├── pgadmin-setup-guide.md
    ├── websocket-security-contract.md
    ├── ai-usage-declaration.md
    ├── security-evidence-checklist.md
    └── audit-log-immutability-verification.md
```

### 1.1 Technology Stack & Standardized Versions
- **Java**: 21 LTS
- **Spring Boot**: 3.4.5
- **Database**: PostgreSQL 16+ (tested on PostgreSQL 16 and 18 with `pgcrypto` and `btree_gist`)
- **Frontend Framework**: Next.js 15 (React 19, TypeScript)
- **Standardized Backend Port**: `8080` (HTTP)
- **Standardized Frontend Port**: `3000` (HTTP)

---

## 2. Core Security & Privacy Implementation (Criteria C, D, I)

### 2.1 AES-256-GCM JPA AttributeConverter
All sensitive personal identifiable information (PII) and clinical data are encrypted at rest using AES-256 in Galois/Counter Mode (GCM).
- **IV Generation**: A fresh, cryptographically strong 12-byte initialization vector (`iv`) is generated via `SecureRandom` for each individual field value.
- **Authentication Tag**: 128-bit authentication tag guarantees confidentiality and integrity.
- **Stored Binary Representation**: `Base64(IV [12 bytes] || Ciphertext || AuthTag [16 bytes])`.
- **Protected Fields**:
  - `UserAccount.fullName`
  - `UserAccount.email`
  - `Patient.nic`, `Patient.dateOfBirth`, `Patient.phone`, `Patient.address`
  - `Appointment.reasonForVisit`
  - `Consultation.diagnosis`, `Consultation.advice`, `Consultation.prescription`
  - `ChatMessage.body`

### 2.2 Blind Indexing with HMAC-SHA256
Because AES-256-GCM produces non-deterministic ciphertext for identical plaintexts, database uniqueness and lookups are performed using deterministic HMAC-SHA256 hashes:
- `UserAccount.emailHmac` on normalized (trimmed, lowercase) email.
- `Patient.nicHmac` on normalized (trimmed, uppercase) NIC.

### 2.3 Account Lockout Mechanism
- **Threshold**: 5 consecutive failed login attempts within an active window.
- **Lockout Duration**: 15 minutes (`locked_until`).
- **Timing Attack Mitigation**: When an unrecognized email is entered, a dummy BCrypt calculation is performed to maintain constant time response.
- **Generic Error Message**: `Invalid email or password` for all authentication failures (preventing username enumeration).
- **Audit Logging**: Success, failure, lockout, and password change events are saved into immutable `audit_logs`.

### 2.4 Server-Side Medical Privacy Guard
Enforces strict medical privacy at both the HTTP servlet filter layer and the service layer:
- `ADMIN` and `MANAGER` roles are categorically blocked from accessing `/api/clinical/**`, `/api/consultations/**`, and `/api/chat/**`.
- Medical records and online consultation chat rooms are strictly restricted to the patient who booked the appointment and the treating doctor assigned to the session.

### 2.5 HttpOnly, Secure & SameSite=Strict Cookie Session Management
- Next.js Route Handlers (`/api/auth/login`, `/api/auth/register`, `/api/auth/logout`, `/api/auth/me`, `/api/auth/change-password`) intercept JWT tokens and set them inside `HttpOnly`, `Secure`, `SameSite=Strict` cookies (`mc_token`).
- **SameSite=Strict Policy**: `SameSite=Strict` is safely used because the frontend and backend are unified behind Next.js route handlers (`/api/auth/*`), eliminating cross-site request forgery without requiring cross-site cookies.
- Prevents XSS-based token theft in client browser environments.

### 2.6 Real Client IP Extraction & Reverse Proxy Integration
- Next.js Route Handlers extract incoming client IP via `extractClientIp` (from `x-forwarded-for`) and forward it to the backend as `X-Forwarded-For`.
- Spring Boot uses `server.forward-headers-strategy: native` to trust the upstream reverse proxy (Render / Next.js) and populate `request.getRemoteAddr()`.
- Client IP addresses are stored in `audit_logs.ip_address` (truncated to 45 chars for IPv6 safety) and are **never** logged alongside patient names or emails in application logs.

### 2.7 Public Catalog Caching & HTTP Compression
- **Caffeine In-Memory Cache**: Configured with a 120-second TTL for `/api/public/specialisations` and `/api/public/doctors`.
- **Cache Isolation**: Resolved Spring proxy self-invocation bug via dedicated `@Component` `PublicDoctorCatalogCache`.
- **HTTP Cache Headers**: Public endpoints return `Cache-Control: public, max-age=120`. All authenticated, clinical, or PII endpoints return `Cache-Control: no-store, no-cache, must-revalidate, private` and `Pragma: no-cache`.
- **Response Compression**: Gzip compression enabled for all JSON/XML payloads exceeding 1024 bytes.

### 2.8 Database Level Immutability & Concurrency Constraints
- **Audit Log Immutability**: PostgreSQL trigger function `prevent_audit_log_change()` rejects any `UPDATE`, `DELETE`, or `TRUNCATE` operations on `audit_logs`.
- **Schedule Overlap Exclusion**: Exclusion constraints on `sessions` using `btree_gist` and `tsrange(session_date + start_time, session_date + end_time)` reject overlapping active sessions for the same doctor or same room.
- **Pessimistic Row-Level Locking**: `ChannelSessionRepository.findByIdForUpdate` provides `@Lock(LockModeType.PESSIMISTIC_WRITE)` for Member 3 booking serialization (BR-03 max_patients cap).
