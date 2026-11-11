# MediChannel — Monorepo Architecture & Security Reference

## 1. System Architecture & Boundaries

```
Hospital Channelling and Appointment System/
├── backend/            # Spring Boot 3 REST API & STOMP WebSockets
├── frontend/           # Next.js 15 (App Router)
├── database/           # PostgreSQL Schema & Seed Migration Scripts
└── docs/               # System documentation & verification evidence
```

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
- **Audit Logging**: Success, failure, and lockout events are saved into immutable `audit_logs`.

### 2.4 Server-Side Medical Privacy Guard
Enforces strict medical privacy at both the HTTP servlet filter layer and the service layer:
- `ADMIN` and `MANAGER` roles are categorically blocked from accessing `/api/clinical/**`, `/api/consultations/**`, and `/api/chat/**`.
- Medical records and online consultation chat rooms are strictly restricted to the patient who booked the appointment and the treating doctor assigned to the session.

### 2.5 HttpOnly & Secure Cookie Session Management
- Next.js Route Handlers (`/api/auth/login`, `/api/auth/register`, `/api/auth/logout`, `/api/auth/me`) intercept JWT tokens and set them inside `HttpOnly`, `Secure`, `SameSite=Strict` cookies.
- Prevents XSS-based token theft in client browser environments.

### 2.6 Public Catalog Caching & HTTP Compression
- **Caffeine In-Memory Cache**: Configured with a 120-second TTL for `/api/public/specialisations` and `/api/public/doctors`.
- **HTTP Cache Headers**: Public endpoints return `Cache-Control: public, max-age=120`. All authenticated, clinical, or PII endpoints return `Cache-Control: no-store, no-cache, must-revalidate, private` and `Pragma: no-cache`.
- **Response Compression**: Gzip compression enabled for all JSON/XML payloads exceeding 1024 bytes.
