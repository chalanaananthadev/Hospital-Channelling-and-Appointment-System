# MediChannel — Security Evidence & Verification Checklist

This document details the exact screenshots, terminal outputs, and automated test reports the **Group Leader (Member 1)** must compile for the final assignment report and viva demonstration.

---

## 1. Criterion C: Cryptographic Implementation (AES-256-GCM + HMAC-SHA256)

- [ ] **Evidence 1.1: Database Ciphertext vs Application Plaintext**
  - **Action**: Run `psql -U postgres -d medichannel -c "SELECT id, email_encrypted, full_name_encrypted, role FROM users LIMIT 3;"`
  - **What to show**: The database stores Base64-encoded binary blobs (`IV || Ciphertext || Tag`) with no readable names or emails.
  - **Counterpart**: Screenshot of `GET /api/auth/me` or `/api/patient/me` showing the decrypted plaintext name and email rendered in JSON.
  - **Key Note**: Highlight that each row has a different random 12-byte IV even if two users had the same name.

- [ ] **Evidence 1.2: HMAC-SHA256 Blind Indexing**
  - **Action**: Run `psql -U postgres -d medichannel -c "SELECT email_hmac FROM users WHERE email_hmac = '...';"`
  - **What to show**: Demonstrates that search queries use deterministic HMAC-SHA256 hashes without ever running `LIKE` queries on plaintext or decrypting whole tables.

- [ ] **Evidence 1.3: Cryptographic Unit Tests Passing**
  - **Action**: Run `mvn test -Dtest=AesGcmAttributeConverterTest,EncryptionServiceTest,HmacSha256HasherTest`
  - **What to show**: All tests green, proving encryption/decryption roundtrips, random IV per call, and HMAC consistency.

---

## 2. Criterion D: Authentication, Rate Limiting & Account Lockout

- [ ] **Evidence 2.1: 5 Consecutive Failed Logins Triggering Lockout**
  - **Action**: Execute 5 failed login requests using `curl` or Postman:
    ```bash
    curl -i -X POST http://localhost:8080/api/auth/login \
      -H "Content-Type: application/json" \
      -d '{"email":"patient1@lankacare.lk","password":"WrongPassword#1"}'
    ```
  - **What to show**: Responses 1 through 5 return `401 Unauthorized` with generic message `Invalid email or password`.
  - **Database Evidence**: Run `SELECT failed_login_attempts, locked_until FROM users WHERE email_hmac = '...';` showing `failed_login_attempts = 5` and `locked_until` set 15 minutes into the future.
  - **6th Attempt Evidence**: Attempting to log in during the 15-minute window still returns `401 Unauthorized` with the generic error, preventing timing-based account enumeration.

- [ ] **Evidence 2.2: Password Change Endpoint Verification**
  - **Action**: Call `POST /api/auth/change-password` with valid bearer token, updating password.
  - **What to show**: Returns `204 No Content`. Database shows `must_change_password = false` and new bcrypt hash.
  - **Audit Evidence**: `SELECT action, entity_type, ip_address FROM audit_logs WHERE action = 'PASSWORD_CHANGED';`

---

## 3. Criterion I: Server-Side Medical Privacy Guard

- [ ] **Evidence 3.1: Manager / Admin Blocked from Clinical Records (403 Forbidden)**
  - **Action**: Execute a request to `/api/clinical/notes` using an ADMIN or MANAGER JWT token:
    ```bash
    curl -i -X GET http://localhost:8080/api/clinical/notes \
      -H "Authorization: Bearer <ADMIN_OR_MANAGER_JWT>"
    ```
  - **What to show**: HTTP response code `403 Forbidden` with JSON body:
    ```json
    {
      "status": 403,
      "error": "Forbidden",
      "message": "Clinical records are restricted to patients and their assigned doctors"
    }
    ```

- [ ] **Evidence 3.2: Patient / Doctor Permitted (200 OK)**
  - **Action**: Repeat request to `/api/clinical/notes` using a CUSTOMER or DOCTOR JWT token.
  - **What to show**: HTTP response code `200 OK` with JSON response.

---

## 4. Criterion J: Audit Log Immutability (Database Triggers)

- [ ] **Evidence 4.1: Database Rejection of Audit Log Tampering**
  - **Action**: In `psql`, attempt an UPDATE and a DELETE on `audit_logs`:
    ```sql
    UPDATE audit_logs SET ip_address = '10.0.0.1' WHERE id = (SELECT id FROM audit_logs LIMIT 1);
    DELETE FROM audit_logs WHERE id = (SELECT id FROM audit_logs LIMIT 1);
    TRUNCATE audit_logs;
    ```
  - **What to show**: PostgreSQL immediately raises an exception:
    `ERROR: audit_logs is immutable`
  - **Significance**: Proves that even a compromised database superuser or SQL injection cannot alter audit trail records.

- [ ] **Evidence 4.2: Real Client IP Forwarding**
  - **Action**: Inspect `SELECT ip_address, action, created_at FROM audit_logs ORDER BY created_at DESC LIMIT 5;`
  - **What to show**: Displays the forwarded client IP (e.g., public or LAN IP) rather than local container loopback `127.0.0.1`.

---

## 5. Criterion G & M: Caching, Compression & Security Headers

- [ ] **Evidence 5.1: Public Catalog In-Memory Caching (Caffeine)**
  - **Action**: Run `mvn test -Dtest=PublicCatalogServiceCacheTest`
  - **What to show**: Unit test proving the database repository is queried exactly once for repeated requests within the 120s TTL window, and re-queried after cache eviction.

- [ ] **Evidence 5.2: HTTP Response Headers (no-store vs public cache)**
  - **Action**: Run `curl -i http://localhost:8080/api/public/specialisations`
    - Look for: `Cache-Control: public, max-age=120`
  - **Action**: Run `curl -i http://localhost:8080/api/auth/me`
    - Look for: `Cache-Control: no-store, no-cache, must-revalidate, private` and `Pragma: no-cache`
  - **Action**: Run `curl -i http://localhost:3000/api/auth/login`
    - Look for: `Set-Cookie: mc_token=...; HttpOnly; Secure; SameSite=Strict; Path=/`

---

## 6. Monorepo Build & Automated Test Summary

- [ ] **Evidence 6.1: Backend Test Suite (37 Passing Tests)**
  - **Action**: Run `mvn clean test` from `backend/` directory.
  - **What to show**: `Tests run: 37, Failures: 0, Errors: 0, Skipped: 0` and `BUILD SUCCESS`.

- [ ] **Evidence 6.2: Frontend Next.js Production Build**
  - **Action**: Run `npm run build` from `frontend/` directory.
  - **What to show**: Successful compilation of static and dynamic server routes with 0 TypeScript/ESLint errors.
