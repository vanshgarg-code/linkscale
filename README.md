# LinkScale - URL Shortener & Analytics Platform

## Phase 1A: Backend Foundation

This repository contains the foundational backend infrastructure for **LinkScale**, establishing build harness, containerized local development services, database migrations with Flyway, and automated integration tests with Testcontainers.

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

