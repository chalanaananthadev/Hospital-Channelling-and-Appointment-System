# MediChannel — pgAdmin 4 Setup & Database Initialization Guide

This guide walks you through connecting **pgAdmin 4**, creating the `medichannel` database, executing the schema and seed data, and verifying the connection from Spring Boot.

---

## 1. Ensure the PostgreSQL Service Is Running

On Windows, PostgreSQL runs as a background service named `postgresql-x64-18` (or your version number).

### Method A: Via Windows Services GUI (Recommended)
1. Press `Win + R`, type **`services.msc`**, and press **Enter**.
2. Scroll down to find **`postgresql-x64-18 - PostgreSQL Server 18`**.
3. If the Status is not **Running**:
   - Right-click on it and click **Start**.
   - (Optional) Right-click -> **Properties** -> set **Startup type** to **Automatic**.

### Method B: Via Administrator PowerShell / Command Prompt
Open PowerShell as **Administrator** and run:
```powershell
net start postgresql-x64-18
```

---

## 2. Open pgAdmin 4 and Connect to Server

1. Open **pgAdmin 4** from your Start menu (or launch `C:\Program Files\pgAdmin 4\runtime\pgAdmin4.exe`).
2. When pgAdmin 4 opens, you will see the **Object Explorer** on the left panel.
3. Click on **Servers** -> **PostgreSQL 18** (or your server name).
4. Enter the master password you set when you installed PostgreSQL (e.g. your `postgres` password).
5. Check **Save Password** if desired, then click **OK**.

---

## 3. Create the `medichannel` Database

1. In the Object Explorer, expand **PostgreSQL 18**.
2. Right-click on **Databases** -> select **Create** -> **Database...**
3. In the dialog that appears:
   - **Database**: `medichannel`
   - **Owner**: `postgres` (or `medichannel`)
   - **Encoding**: `UTF8`
4. Click **Save**.
5. You should now see `medichannel` listed under **Databases**.

---

## 4. Run `schema.sql` to Create All 13 Tables

1. In Object Explorer, click to select the **`medichannel`** database.
2. In the top navigation bar, click **Tools** -> **Query Tool** (or right-click `medichannel` and choose **Query Tool**).
3. Open the file:
   - Click the **Open File** icon (folder icon on the query editor toolbar).
   - Browse to: `database/schema.sql` (or `backend/src/main/resources/db/schema.sql`).
   - Alternatively, open `database/schema.sql` in VS Code or Notepad, copy all text, and paste it into the pgAdmin 4 Query Editor.
4. Click the **Execute / Run** button (the triangle ▶ icon or press **F5**).
5. The output console at the bottom will display:
   ```
   CREATE EXTENSION
   CREATE TABLE
   ...
   Query returned successfully in ... msec.
   ```

### Created Tables:
Under **`medichannel` -> Schemas -> public -> Tables**, you will see all **13 required tables**:
- `users`
- `patients`
- `specialisations`
- `doctors`
- `rooms`
- `sessions`
- `appointments`
- `consultations`
- `chat_messages`
- `notifications`
- `feedback`
- `notices`
- `audit_logs`

---

## 5. Run `seed-data.sql` to Insert Reference Data

1. In the same Query Tool (or open a new Query Tool on `medichannel`), clear the editor.
2. Open or copy the contents of **`database/seed-data.sql`**:
   - Seeds 15 hospital specialisations (Cardiology, Dermatology, ENT, etc.)
   - Seeds 5 hospital consultation rooms (Room 101, Room 102, etc.)
3. Click **Execute (F5)** ▶.
4. Output will confirm the rows are inserted.

---

## 6. Configure Spring Boot Backend Connection

In `backend/src/main/resources/application.yml` (or via `.env` file):

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/medichannel?sslmode=disable
    username: postgres
    password: YOUR_POSTGRES_PASSWORD_HERE
```

If you set an environment variable or create a `backend/.env` file:
```env
DATABASE_URL=jdbc:postgresql://localhost:5432/medichannel?sslmode=disable
DATABASE_USERNAME=postgres
DATABASE_PASSWORD=YOUR_POSTGRES_PASSWORD_HERE
```

---

## 7. Connecting to Cloud Neon PostgreSQL (For Production / Deployment)

When deploying to Neon (or when testing cloud database):
1. Sign in to your [Neon Console](https://console.neon.tech).
2. Create a project named `medichannel`.
3. Copy your Neon connection string (JDBC format).
4. Set the environment variables:
   ```env
   DATABASE_URL=jdbc:postgresql://<neon-endpoint>.neon.tech/neondb?sslmode=require
   DATABASE_USERNAME=neondb_owner
   DATABASE_PASSWORD=<your-neon-password>
   ```
5. In Neon SQL Editor, paste and run `database/schema.sql` and `database/seed-data.sql`.

