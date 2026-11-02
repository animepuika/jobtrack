# JobTrack application

A Spring Boot web app for tracking job applications: save jobs by hand or from a
HiringCafe search, move each one through its stages, keep notes, and see
everything on a dashboard. It covers milestones 1–5 of the
[project plan](../docs/project-plan.md).

Built with Java 25, Spring Boot 4.1.2, PostgreSQL, Flyway, Spring Data JPA,
Spring Security, and Thymeleaf.

## Run it

You need JDK 25 and Docker. Commands run from this `app` folder; on Windows use
`mvnw.cmd` or `.\mvnw` instead of `./mvnw`.

**Demo with fictional data** (starts its own PostgreSQL in Docker):

```bash
./mvnw spring-boot:test-run
```

Open <http://localhost:8080> and sign in as `demo` / `demo-password`. Live
HiringCafe search is off in the demo.

**Personal use** with a database that keeps your data:

```bash
docker compose up -d
JOBTRACK_USERNAME=you JOBTRACK_PASSWORD='a long password' ./mvnw spring-boot:run
```

The account is created on the first start. Your records live in the
`jobtrack-data` Docker volume, outside Git.

### Configuration

| Environment variable | Default | Meaning |
| --- | --- | --- |
| `JOBTRACK_DB_URL` | `jdbc:postgresql://localhost:5432/jobtrack` | Database |
| `JOBTRACK_DB_USERNAME` / `JOBTRACK_DB_PASSWORD` | `jobtrack` / `jobtrack` | Database login (local container only) |
| `JOBTRACK_USERNAME` / `JOBTRACK_PASSWORD` | none | Account to create at startup; password needs 8+ characters |
| `JOBTRACK_HIRINGCAFE_ENABLED` | `true` (`false` in the demo profile) | Live job search |
| `JOBTRACK_TIME_ZONE` | `Europe/Riga` | Decides what "today" is |

## Test it

```bash
./mvnw test
```

81 tests: business rules, URL handling, the HiringCafe adapter against a local fake
server, and integration tests that run the whole app against a real PostgreSQL
started by Testcontainers. No test calls the real HiringCafe.

Without Docker, point the tests at an existing PostgreSQL instead:

```bash
JOBTRACK_TEST_TESTCONTAINERS=false JOBTRACK_DB_URL=jdbc:postgresql://localhost:5432/jobtrack_test ./mvnw test
```

## How it is organized

```text
com.jobtrack
├── tracking    the core: jobs, applications, stage rules, history (no web code)
├── jobsource   HiringCafe adapter behind a JobSource interface, and job import
├── user        accounts, password hashing, sign-in lookup
├── api         JSON API under /api (HTTP Basic, no session)
├── web         Thymeleaf pages (login form, session, CSRF)
├── config      security rules and the clock
└── demo        fictional demo data, only in the "demo" profile
```

Requests flow one way: `web`/`api` → `tracking`/`jobsource` → database. The
`tracking` package knows nothing about HTTP or HiringCafe.

### Data

Flyway creates the schema from
[V1__create_tracker_tables.sql](src/main/resources/db/migration/V1__create_tracker_tables.sql):

| Table | Holds |
| --- | --- |
| `app_user` | Accounts, with hashed passwords |
| `tracked_job` | What the job is: title, company, location, link, source and provider ID |
| `job_application` | The user's progress on it: stage, applied date, archived flag |
| `application_event` | History: created, each stage change, notes. Only added to, never edited |

Rules the database enforces as a last line of defense: no blank titles, one
application per job, and no duplicate jobs per user (same HiringCafe ID, or the
same normalized link for manual entries).

### Stage rules

```text
SAVED ──> APPLIED ──> INTERVIEW ──> OFFER ──> ACCEPTED
  │          │            │           │
  └──────────┴────────────┴───────────┴──> WITHDRAWN
             └────────────┴───────────┴──> REJECTED
```

A stage change is refused if the move is not on this diagram, is dated in the
future, or is dated before the previous change.

### JSON API

All endpoints need HTTP Basic authentication. Errors are
[problem details](https://www.rfc-editor.org/rfc/rfc9457) JSON.

| Method and path | Does |
| --- | --- |
| `GET /api/applications?stage=&q=&includeArchived=` | List and filter |
| `POST /api/applications` | Add a job by hand (409 with `existingApplicationId` if already tracked) |
| `GET /api/applications/{id}` | One application with its history |
| `PATCH /api/applications/{id}` | Edit title, company, location |
| `PATCH /api/applications/{id}/stage` | Move stage: `{"stage": "APPLIED", "date": "2026-11-02"}` |
| `POST /api/applications/{id}/notes` | Add a note |
| `POST /api/applications/{id}/archive`, `/restore` | Hide or show on the dashboard |
| `GET /api/sources/hiringcafe/jobs?keywords=&location=&workplace=&seniority=` | Live search |
| `POST /api/imports/hiringcafe` | Track a search result (201 new, 200 already tracked) |

Example:

```bash
curl -u demo:demo-password http://localhost:8080/api/applications
```

The plan proposed separate `/api/jobs` endpoints. Every saved job has exactly one
application, so they are combined under `/api/applications`.

## HiringCafe integration

The adapter sends MCP `tools/call` requests for `search_jobs` straight to
`https://hiringcafe.com/api/mcp` as JSON-RPC over HTTP, without an MCP SDK or AI
model. It reads the structured job cards in the response, and:

- times out after 3 s to connect and 10 s to read, and retries once on network
  errors and 5xx responses;
- does not retry on HTTP 429 and shows the `Retry-After` wait;
- treats `isError: true` responses (sent with HTTP 200) as failures;
- removes `utm_*` tracking parameters from links and uses the HiringCafe job ID
  to recognize jobs that are already tracked;
- leaves notes, stage and history alone when the same job is imported again.

Only jobs the user chooses are saved. When search fails, manual entry still works.

## Not built yet

CV uploads, reminders, data export, a Docker image, CI, and the Azure deployment
are later milestones (6–10).
