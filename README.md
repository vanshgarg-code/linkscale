# LinkScale - URL Shortener & Analytics Platform

LinkScale is a URL-shortening platform built with Java 21, Spring Boot, and
PostgreSQL. The backend is available through a REST API. The web frontend
is planned for Phase 2.

## What works today

- Signed-in users can shorten URLs with a generated code or a custom alias.
  Links can have an optional title and expiration date.
- Anyone can follow an active short link without signing in. Expired,
  inactive, and deleted links stop redirecting.
- Users can register, sign in, check their current account, and sign out.
  Authentication uses server-side sessions and CSRF protection.
- Users can list, search, filter, edit, deactivate, and soft-delete their
  own links. Management requests cannot access another account's links.

## API overview

| Endpoint | Purpose |
| --- | --- |
| `GET /api/v1/auth/csrf` | Get a CSRF token before submitting an auth request |
| `POST /api/v1/auth/register` | Register an account |
| `POST /api/v1/auth/login` | Start a signed-in session |
| `GET /api/v1/auth/me` | Get the signed-in account |
| `POST /api/v1/auth/logout` | End the session |
| `POST /api/v1/links` | Create a link for the signed-in account |
| `GET /api/v1/links` | List owned links with search, status filters, and pagination |
| `GET /api/v1/links/{id}` | Get an owned link |
| `PATCH /api/v1/links/{id}` | Update a link's title, expiration, or active status |
| `DELETE /api/v1/links/{id}` | Soft-delete an owned link |
| `GET /r/{code}` | Follow a public short link |
| `GET /actuator/health` | Check backend health |

For example, `GET /api/v1/links?search=docs&status=active&page=0&size=20`
returns the signed-in account's active links matching `docs`.

## Roadmap

- [x] Phase 0 — Architecture and planning
- [x] Phase 1A — Backend foundation
- [x] Phase 1B — URL shortening and public redirects
- [x] Phase 1C — Authentication and owner-scoped link management
- [ ] Phase 2 — Web frontend and shortening flow
- [ ] Phase 3 — Authenticated dashboard
- [ ] Phase 4 — Analytics, Redis caching, and rate limiting
- [ ] Phase 5 — End-to-end testing and hardening
- [ ] Phase 6 — Deployment and portfolio release

### Technology Stack
* **Java**: 21 (LTS)
* **Spring Boot**: 4.1.2
* **Build System**: Apache Maven 3.9.16 (via Maven Wrapper under `backend/`)
* **Database**: PostgreSQL 17.11 (`postgres:17.11-alpine`)
* **Database Migrations**: Spring Boot Starter Flyway + `flyway-database-postgresql`
* **Testing**: JUnit Jupiter 6.0.3, AssertJ, Spring Boot Starter Test, Spring Boot Testcontainers & Testcontainers 2.x
* **Orchestration**: Docker Compose v2 (`compose.yaml`)

---

### Project Structure
```
LinkScale/
├── compose.yaml              # Docker Compose for local PostgreSQL 17.11-alpine
├── .env.example              # Environment variables template
├── .gitignore                # Git ignore rules
├── README.md                 # Project documentation & runbook
└── backend/                  # Spring Boot application root
    ├── .mvn/wrapper/         # Maven wrapper definition (Maven 3.9.16)
    ├── mvnw                  # Linux/macOS wrapper script
    ├── mvnw.cmd              # Windows PowerShell/cmd wrapper script
    ├── pom.xml               # Spring Boot 4.1.2 parent POM & dependencies
    └── src/
        ├── main/
        │   ├── java/         # Application source (LinkScaleApplication)
        │   └── resources/
        │       ├── application.yml        # Base configuration (Virtual Threads off, Actuator health only)
        │       ├── application-dev.yml    # Development profile (reads env vars for Compose DB)
        │       └── db/migration/
        │           └── V1__init_schema.sql # Baseline schema: users & links
        └── test/
            ├── java/         # Tests (DatabaseMigrationIT, LinkScaleApplicationTests)
            └── resources/
                └── application-test.yml   # Test profile configuration
```

---

### Local Setup & Verification (Windows PowerShell)

#### 1. Prerequisites Check
Run the following in Windows PowerShell to verify environment readiness:
```powershell
# 1. Confirm Java 21 JDK
java -version

# 2. Confirm Docker daemon is running
docker info --format '{{.ServerVersion}}'

# 3. Pull PostgreSQL 17.11 Alpine image
docker pull postgres:17.11-alpine

# 4. Confirm Maven Wrapper resolves
cd backend
.\mvnw.cmd -v
cd ..
```

#### 2. Start PostgreSQL via Docker Compose
From the repository root:
```powershell
# Copy environment configuration
Copy-Item .env.example .env

# Start PostgreSQL service in background
docker compose up -d

# Verify container status is healthy
docker compose ps
```
The port is bound strictly to `127.0.0.1:5432`.

#### 3. Run Automated Tests & Failsafe Integration Tests
The integration tests (`DatabaseMigrationIT`) are backed by Testcontainers and dynamically spin up their own isolated PostgreSQL container on an ephemeral port. They do **not** depend on or interfere with the Docker Compose instance.
```powershell
cd backend
.\mvnw.cmd clean verify
```
This executes:
1. `maven-surefire-plugin` (unit & context load tests)
2. `maven-failsafe-plugin` (PostgreSQL 17.11 migration integration tests)

> **Note on Docker Availability:** If Docker Desktop is not running, Testcontainers cannot initialize its container and will raise a `DockerClientException`. The integration test phase will be reported as **BLOCKED** until Docker is started.

#### 4. Run Spring Boot Application Locally
```powershell
cd backend
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev
```

#### 5. Smoke Test Actuator Health
In a separate PowerShell session:
```powershell
Invoke-RestMethod -Uri http://localhost:8080/actuator/health
```
Expected response:
```json
{
  "status": "UP"
}
```

#### 6. Stop Local Services
When finished, stop the Docker Compose container (volumes are preserved):
```powershell
docker compose down
```

