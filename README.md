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
| `GET /api/v1/links/{id}/analytics?days=30` | Get a click total and daily counts for an owned link |
| `PATCH /api/v1/links/{id}` | Update a link's title, expiration, or active status |
| `DELETE /api/v1/links/{id}` | Soft-delete an owned link |
| `GET /r/{code}` | Follow a public short link |
| `GET /actuator/health` | Check backend health |

For example, `GET /api/v1/links?search=docs&status=active&page=0&size=20`
returns the signed-in account's active links matching `docs`.

Link analytics accepts `days` from 1 through 90, with a default of 30.
The window includes today in UTC and returns daily counts in date order,
including zero-click days. `total_clicks` is the sum for that window.
Each successful GET redirect can contribute one click, including repeat visits.
A signed-out request gets 401. A missing, deleted, or another user's link gets 404.

In the signed-in dashboard, choose **Analytics** on a link to see its click
total, daily chart, and daily counts for the last 7, 30, or 90 days.

## Redis redirect cache

The development profile uses Redis on port 6379. Start PostgreSQL and Redis
with `docker compose up -d`. A cache miss reads PostgreSQL, and a cache hit
still records a click for each successful GET. Entries last at most 60 seconds
and never outlive the link's expiry. Link updates and deletions evict entries.

The cache is designed for a single application instance. On a failed eviction,
LinkScale uses PostgreSQL until the application restarts so a recovered Redis
cannot serve the stale entry. Redis is optional for application health. Set
`LINKSCALE_REDIRECT_CACHE_ENABLED=false` to run the dev app without the cache.

With the app running, visit an active short link twice, then run
`docker compose exec redis redis-cli --scan --pattern "linkscale:redirect:*"`.
The link should have a Redis key, and analytics should show both clicks.
Deactivate it and confirm the URL returns 404. Reactivate it, stop Redis with
`docker compose stop redis`, and confirm the URL still redirects. Restore the
service with `docker compose start redis`.

## API rate limits

Redis enforces a 60-second window starting with the first request in each
bucket. Login and registration use the direct client IP as seen by Spring Boot.
Link creation uses the signed-in account ID.

| Action | Requests per window |
| --- | ---: |
| Login | 10 per IP |
| Registration | 5 per IP |
| Create a short link | 30 per account |

Requests that reach these controllers count even if credentials or link
details are rejected later. A full bucket returns HTTP 429 and a
`Retry-After` header in seconds. If Redis cannot check a limit, these three
actions return HTTP 503 until Redis is reachable again. Public redirects
continue to use their PostgreSQL fallback.

Forwarded IP headers are ignored. Behind a proxy, anonymous limits share
the proxy IP until trusted forwarding is configured. Start Redis with
`docker compose up -d redis`. For local work without Redis, set
`LINKSCALE_RATE_LIMIT_ENABLED=false` before starting Spring Boot.

## Browser smoke test

The Playwright test signs up a fresh account (signup also signs the user in),
shortens a local URL, follows it as a signed-out visitor, and checks that
the dashboard shows one recorded click. Use a local development database.
The test creates one account and one link per run.

Start Docker Desktop, then run from the repository root in PowerShell:

```powershell
docker compose up -d
Set-Location backend
$env:SPRING_PROFILES_ACTIVE = 'dev'
.\mvnw.cmd spring-boot:run
```

Once Spring Boot is listening on port 8080, open a second PowerShell
window in the repository root:

```powershell
Set-Location frontend
npm.cmd ci
npx.cmd playwright install chromium
npm.cmd run test:e2e
```

Playwright starts and stops Vite on port 4173. If repeated runs hit the
registration rate limit, wait 60 seconds before rerunning. On failure,
the trace is saved under `frontend/test-results/`.

## Production configuration

Activate the `prod` Spring profile on the host. Serve the frontend and
backend through the same HTTPS origin. Route `/api/**`, `/r/**`, and
`/actuator/health` to Spring Boot and serve the built frontend for page
routes, including direct visits to `/login`, `/register`, and `/dashboard`.

Set these values in the host's environment or secret settings:

| Variable | Value |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `LINKSCALE_BASE_URL` | Public HTTPS origin, such as `https://links.example.com`, without a trailing slash |
| `SPRING_DATASOURCE_URL` | PostgreSQL JDBC URL with the database provider's TLS settings |
| `SPRING_DATASOURCE_USERNAME` | Database username |
| `SPRING_DATASOURCE_PASSWORD` | Database password |
| `SPRING_DATA_REDIS_URL` | Provider connection URL, including credentials if required. Use `rediss://` for TLS |
| `LINKSCALE_TRUSTED_PROXIES` | Regex or CIDR list for the actual reverse proxy's source addresses |
| `PORT` | Host-assigned backend port. Defaults to `8080` |

The trusted proxy setting defaults to a regex that matches no addresses.
Set it after selecting the hosting topology. For a proxy running on the
same machine and connecting over IPv4 loopback, use `127\\.0\\.0\\.1`.
The proxy must overwrite client-supplied forwarding headers, and the
backend port must be reachable only from the trusted proxy. Forwarded
client addresses determine the anonymous login and registration budgets.

Production session and CSRF cookies use `Secure` and `SameSite=Lax`.
The session cookie also uses `HttpOnly`. Sessions expire after 30 minutes
of inactivity. Redis caching and rate limiting are enabled, and Flyway
applies migrations on startup. Environment values must be supplied by
the host. Spring Boot does not automatically load the Compose `.env` file.

Run one backend instance for the initial release. Sessions are currently
held in that instance's memory, and cache invalidation assumes one
instance. Backend restarts require users to sign in again.

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
├── compose.yaml              # Docker Compose for local PostgreSQL and Redis
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

