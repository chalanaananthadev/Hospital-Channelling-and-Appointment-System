# Generative AI Usage Declaration

**Course**: IT404050 — Full Stack Development  
**Assignment**: Assignment 2 — Hospital Channelling and Appointment System (MediChannel)  
**Student Name**: [YOUR FULL NAME HERE]  
**Student Registration ID**: [YOUR IT NUMBER HERE, e.g., IT21XXXXXX]  
**Group Role**: Group Leader / Member 1 (Architecture, Security, Caching, Infrastructure)  
**Date**: November 2026  

---

## 1. AI Tools & Models Utilized

- **Tool(s)**: Google Antigravity (Powered by Gemini 2.5 / Claude 3.7 Sonnet)
- **Interface**: Antigravity Autonomous Agent IDE & Chat Assistant
- **Target Repository Component**: Monorepo Root, Backend Core Architecture (`backend/`), Database Security & Triggers (`database/`), Frontend Security Middleware & Cookie Handlers (`frontend/`)

---

## 2. Scope of AI Usage (Member 1 Domain)

AI assistance was selectively utilized to accelerate architectural design, cryptographic implementations, and security hardening under the guidance and verification of the author:

1. **Monorepo & Build Orchestration**:
   - Initial layout structuring for Next.js 15 App Router and Spring Boot 3.4.5 Maven multi-directory workspace.
   - Container configuration with `docker-compose.yml` and environment variable enforcement.
2. **Cryptographic Protection & JPA Converters**:
   - Designing the `AesGcmAttributeConverter` generating cryptographically random 12-byte IVs per field value with 128-bit authentication tags.
   - Implementing normalized deterministic HMAC-SHA256 search hashes (`HmacSha256Hasher`) for email and NIC lookups without exposing plaintext to database indexes.
3. **Authentication & Session Security**:
   - Spring Security 6 stateless JWT filter chain (`JwtAuthenticationFilter`) and Next.js Route Handlers issuing `HttpOnly`, `Secure`, `SameSite=Strict` cookies.
   - Account lockout enforcement (5 consecutive failed attempts triggering a 15-minute lock with timing-safe dummy BCrypt calculation).
   - Password change endpoint with bcrypt validation, reuse prevention, and `@Size(max = 72)` protection.
4. **Medical Privacy & Regulatory Safeguards**:
   - Implementing `MedicalPrivacyFilter` and `MedicalPrivacyGuard` blocking administrative roles (`ADMIN`, `MANAGER`) from accessing clinical consultation notes, chat, or medical files.
5. **Caching & Concurrency Hardening**:
   - Resolving Spring proxy self-invocation bug via dedicated `@Component` `PublicDoctorCatalogCache` with Caffeine cache.
   - Adding PostgreSQL row-level pessimistic locking helpers (`findByIdForUpdate`) and GIST exclusion constraints.
6. **Immutable Audit Logging**:
   - Writing PostgreSQL trigger functions (`prevent_audit_log_change`) that reject `UPDATE`, `DELETE`, and `TRUNCATE` operations on `audit_logs`.

---

## 3. Prompt Engineering Strategy & Iteration

- **Context-Specific Role Prompting**: Prompts specified the precise role (Group Leader / Member 1) and explicit project boundaries to prevent unauthorized implementation of feature modules assigned to Members 2, 3, 4, and 5.
- **Fail-Fast Security Specifications**: Explicitly instructed the model to avoid committing secrets, removing hard-coded defaults, and validating configuration variables at application bootstrap.
- **Verification Driven Development**: Every AI-generated component was required to be accompanied by automated unit tests or PostgreSQL verification queries prior to acceptance.

---

## 4. Verification & Validation Methods

All code generated or refined by AI was rigorously tested and verified through independent manual and automated methods:

1. **Automated Unit & Integration Test Suites**:
   - 37 automated backend tests executed via Maven Surefire (including `SecurityIntegrationTest`, `AuthServiceTest`, `PublicCatalogServiceCacheTest`, `GlobalExceptionHandlerTest`, `AesGcmAttributeConverterTest`, `EncryptionServiceTest`, `HmacSha256HasherTest`).
   - Frontend Next.js production build (`npm run build`) ensuring zero TypeScript compilation or route typing errors.
2. **Live Database Triggers & Exclusion Testing**:
   - Verified audit log immutability via `psql` executing `UPDATE` and `DELETE` on `audit_logs` and confirming `ERROR: audit_logs is immutable`.
   - Verified schedule exclusion constraints rejecting overlapping doctor sessions using `btree_gist` and `tsrange`.
3. **Static Secret Scanning**:
   - Scanned the entire git repository using `git grep` regex patterns to ensure zero committed secrets or realistic credentials.

---

## 5. Author Reflection on Learning

Through this assignment, I learned:
- How AES-256 in Galois/Counter Mode (GCM) differs fundamentally from CBC and ECB, specifically why a fresh initialization vector is mandatory for every single plaintext encryption to prevent ciphertext difference attacks.
- Why deterministic blind indexing using HMAC-SHA256 with an independent pepper key is essential for performing exact match database queries on encrypted data without storing plaintext.
- The mechanics of Spring AOP proxying and why calling an `@Cacheable` method from within the same class bypasses the interceptor proxy, requiring dedicated cache delegate components.
- How to coordinate cross-functional boundaries in a team project so that shared security infrastructure, row-level locking, and immutable audit logs seamlessly support subsequent business logic developed by team members.

---

**Student Signature**: ________________________  
**Date**: November 2026
