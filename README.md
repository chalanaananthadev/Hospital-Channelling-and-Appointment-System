# Lanka Care Hospital — MediChannel

A secure, real-time hospital channelling and appointment system built with **Next.js (App Router)**, **Spring Boot 3**, and **PostgreSQL**.

---

## 1. System Overview

MediChannel solves the manual channelling bottlenecks of Lanka Care Hospital, Colombo:
- **Patients (CUSTOMER)**: Search doctors, see live remaining slots, book physical or online appointments, follow real-time queue status ("now serving"), consult via private encrypted WebSockets, read prescriptions, and submit feedback.
- **Doctors**: Manage daily session queues, call next patient, conduct encrypted consultations, write clinical advice/prescriptions, mark completed or no-show.
- **Managers**: Manage doctor profiles, specialisations, rooms, sessions, reception check-in (arrival & payment), walk-in bookings, notices, and analytics.
- **Administrators**: Create and manage doctor/manager accounts, reset staff passwords, inspect immutable audit logs.
- **Medical Privacy Guard**: Strict server-side enforcement blocking ADMIN and MANAGER roles from ever accessing clinical notes, prescriptions, reasons for visit, or chat logs.

---

## 2. Directory Structure (Monorepo)

```
Hospital Channelling and Appointment System/
├── backend/                     # Spring Boot 3 REST API & STOMP WebSockets
│   ├── src/main/java/com/medichannel/
│   │   ├── bootstrap/          # Startup data seeding & initial Admin creation
│   │   ├── config/             # Security, Cache, WebMvc, WebSockets
│   │   ├── controller/         # REST Controllers (Auth, Public, Health, etc.)
│   │   ├── dto/                # Data Transfer Objects
│   │   ├── entity/             # 13 JPA Entities
│   │   ├── enums/              # Roles, statuses, actions
│   │   ├── exception/          # Global Exception Handler (standard JSON error format)
│   │   ├── repository/         # Spring Data JPA Repositories
│   │   ├── security/           # AES-256-GCM converter, HMAC-SHA256 hasher, JWT filters
│   │   ├── service/            # Core business services
│   │   └── validation/         # Custom Sri Lankan NIC, phone, and password validators
│   ├── src/main/resources/
│   │   ├── db/                 # schema.sql and seed-data.sql
│   │   └── application.yml     # Application configuration
│   ├── Dockerfile              # Multi-stage production container build
│   └── pom.xml
├── frontend/                    # Next.js 15 (App Router)
│   ├── src/app/                # Route groups ((public), patient, doctor, manager, admin)
│   │   └── api/auth/           # Route Handlers for HttpOnly & Secure JWT cookies
│   ├── src/lib/                # API helpers and cookie managers
│   ├── Dockerfile              # Multi-stage container build
│   └── package.json
├── database/                    # Database definitions and migration scripts
│   ├── schema.sql              # 13 tables, keys, constraints, and indexes
│   └── seed-data.sql           # Reference specialisations & rooms
├── docs/                        # Architecture, ERD, and pgAdmin guides
│   ├── architecture.md
│   ├── erd.md
│   └── pgadmin-setup-guide.md
├── docker-compose.yml           # Local multi-container orchestration
├── .env.example                 # Environment variable templates
└── README.md
```

---

## 3. Quick Start: Local PostgreSQL & pgAdmin 4

### Step 1: Ensure PostgreSQL Service Is Running
Open Windows PowerShell as Administrator:
```powershell
net start postgresql-x64-18
```
*(Or open `services.msc` and ensure `postgresql-x64-18` is Started)*.

### Step 2: Initialize Database via pgAdmin 4
1. Open **pgAdmin 4**.
2. Connect to your local PostgreSQL server with your master password.
3. Right-click **Databases** -> **Create** -> **Database...** -> name it **`medichannel`**.
4. Open the **Query Tool** on `medichannel`:
   - Open and execute `database/schema.sql` (Creates all 13 tables).
   - Open and execute `database/seed-data.sql` (Seeds specialisations and rooms).
5. For full details with screenshots/tips, see [docs/pgadmin-setup-guide.md](file:///d:/sem 4/Full stack development/assigment2/Hospital Channelling and Appointment System/docs/pgadmin-setup-guide.md).

### Step 3: Configure Environment Variables
Copy `.env.example` to `.env` (or configure in your shell / IDE):
```env
DATABASE_URL=jdbc:postgresql://localhost:5432/medichannel?sslmode=disable
DATABASE_USERNAME=postgres
DATABASE_PASSWORD=YOUR_PASSWORD_HERE
AES_256_KEY=dGhpc0lzQTMyeXRlTG9uZ0FFUzI1NktleUZvclRlc3Rpbmc=
HMAC_SHA256_KEY=dGhpc0lzQVRlc3RITUFDU2hhMjU2S2V5TXVzdEJlMzJCeXRlc0xvbmc=
JWT_SECRET=superSecretKeyForMediChannelJwtMustBeAtLeast32BytesLong!
```

---

## 4. Running with Docker Compose (Alternative)

If you have Docker Desktop installed, run the entire stack (PostgreSQL + Backend + Frontend):
```bash
docker compose up -d --build
```
- Frontend: `http://localhost:3000`
- Backend API: `http://localhost:8080/api`
- Health check: `http://localhost:8080/api/health`

---

## 5. Security Architecture Highlights (Leader Implementation)

1. **AES-256-GCM JPA AttributeConverter**:
   - Random 12-byte IV generated per field write (`SecureRandom`).
   - Ciphertext authenticated with 128-bit GCM tag.
   - Encrypts: patient name, email, NIC, date of birth, phone, address, reason for visit, diagnosis, advice, prescription, chat messages.
2. **HMAC-SHA256 Blind Indexing**:
   - Generates deterministic hash for `email` and `nic` to allow fast index lookup and unique constraint enforcement without decrypting columns.
3. **Account Lockout**:
   - 5 failed login attempts trigger an automatic 15-minute lock.
   - Constant-time dummy password comparison on unknown email to eliminate user enumeration / timing attacks.
   - Generic error returned: `Invalid email or password`.
4. **Server-Side Medical Privacy Guard**:
   - Hardened `MedicalPrivacyFilter` and `MedicalPrivacyGuard` blocking ADMIN and MANAGER from clinical endpoints and records.
5. **HttpOnly Secure Cookies**:
   - Next.js Route Handlers (`/api/auth/login`, `/api/auth/register`, `/api/auth/logout`) manage JWT tokens inside `HttpOnly`, `Secure`, `SameSite=Strict` cookies.
6. **Public Catalog Caching & Compression**:
   - In-memory Caffeine cache with 120s TTL for doctors and specialisations.
   - `Cache-Control: public, max-age=120` for catalog; `Cache-Control: no-store` for all PII and clinical data.
   - Gzip compression enabled for all JSON responses > 1KB.

---

## 6. Live Deployment Links

| Component | Platform | URL |
| :--- | :--- | :--- |
| **Frontend** | Vercel | *TBD (Deploying in Week 4)* |
| **Backend API** | Render / Railway | *TBD (Deploying in Week 4)* |
| **Database** | Neon PostgreSQL | *TBD (Connected)* |
