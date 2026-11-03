# JobTrack application

A Spring Boot web app for tracking job applications: save jobs by hand or from a
HiringCafe search, move each one through its stages, keep notes, record which CV
you sent, set follow-up reminders, export your records, and see everything on a
dashboard. It covers milestones 1–7 of the [project plan](../docs/project-plan.md).

Built with Java 25, Spring Boot 4.1.2, PostgreSQL, Flyway, Spring Data JPA,
Spring Security, and Thymeleaf. The interface follows a "quiet utility" design made
with Claude Design from [the design brief](../docs/design-prompt.md): one plain CSS
file with light and dark themes, stage badges that use a glyph as well as color,
44 px touch targets, and no JavaScript.

## Run it

You need JDK 25 and Docker. Commands run from this `app` folder; on Windows use
`mvnw.cmd` or `.\mvnw` instead of `./mvnw`.

**Demo with fictional data** (starts its own PostgreSQL in Docker):

```bash
./mvnw spring-boot:test-run
```

Open <http://localhost:8080> and sign in as `demo` / `demo-password`. Live
HiringCafe search is off in the demo.

**Personal use** with databases that keep your data. `docker compose up` starts both
PostgreSQL databases, Azurite (the local queue) and the
[reminder service](../reminder-service/README.md); the tracker runs from this folder:

```bash
docker compose up -d --build
JOBTRACK_USERNAME=you JOBTRACK_PASSWORD='a long password' ./mvnw spring-boot:run
```

The account is created on the first start. Your records live in the
`jobtrack-data` Docker volume and uploaded CVs in `app/data/`, both outside Git.

**Everything in containers** (the same plus the tracker image built from the
[Dockerfile](Dockerfile)):

```bash
JOBTRACK_USERNAME=you JOBTRACK_PASSWORD='a long password' docker compose --profile app up --build
```

Uploaded CVs then live in the `jobtrack-documents` volume.

### Configuration

| Environment variable | Default | Meaning |
| --- | --- | --- |
| `JOBTRACK_DB_URL` | `jdbc:postgresql://localhost:5432/jobtrack` | Database |
| `JOBTRACK_QUEUE_CONNECTION_STRING` | local Azurite | Queue for reminder events |
| `JOBTRACK_REMINDERS_URL` | `http://localhost:8081` | Reminder service |
| `REMINDERS_INTERNAL_TOKEN` | `local-development-token` | Shared with the reminder service; set a long random value outside your PC |
| `JOBTRACK_DB_USERNAME` / `JOBTRACK_DB_PASSWORD` | `jobtrack` / `jobtrack` | Database login (local container only) |
| `JOBTRACK_USERNAME` / `JOBTRACK_PASSWORD` | none | Account to create at startup; password needs 8+ characters |
| `JOBTRACK_HIRINGCAFE_ENABLED` | `true` (`false` in the demo profile) | Live job search |
| `JOBTRACK_TIME_ZONE` | `Europe/Riga` | Decides what "today" is and how reminder times are entered and shown |
| `JOBTRACK_DOCUMENTS_DIR` | `data/documents` | Folder for uploaded CV files |

## Test it

```bash
./mvnw test
```

164 tests: business rules, URL handling, the HiringCafe adapter against a local fake
server, search filters, paging and saved searches, the page states (welcome, paging,
errors, final stages), CV upload checks and private downloads, CV reading and skill
matching, progress figures and follow-ups with a fixed clock, the reminder outbox under
failures, the export, and integration tests that run the whole app against a real PostgreSQL
started by Testcontainers. No test calls the real HiringCafe.

Without Docker, point the tests at an existing PostgreSQL instead:

```bash
JOBTRACK_TEST_TESTCONTAINERS=false JOBTRACK_DB_URL=jdbc:postgresql://localhost:5432/jobtrack_test ./mvnw test
```

## How it is organized

GitHub Actions ([ci.yml](../.github/workflows/ci.yml)) runs the same tests on every
push and pull request, with Testcontainers starting PostgreSQL 18, then builds the
Docker image and checks that it contains only the app.

```text
com.jobtrack
├── tracking    the core: jobs, applications, stage rules, history (no web code)
├── jobsource   HiringCafe adapter behind a JobSource interface, and job import
├── documents   CV versions: upload checks, private file storage, ownership
├── matching    skills from the CV, skill catalog, fit scoring
├── reminders   reminder commands and reads; the reminders themselves live in the reminder service
├── events      transactional outbox and the publisher that sends events to the queue
├── export      collects one user's records into a JSON export
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
| `cv_version` | Uploaded CV details and a random storage key; the file itself is in document storage |
| `reminder_ref` | Which reminders exist, whose they are, their latest event version and whether they are open. The reminders themselves are in the reminder service's database |
| `outbox_event` | Events waiting to be sent to the queue, or already sent |
| `skill_profile`, `user_skill` | The user's skills and which CV they were read from; never the CV text |
| `saved_search` | Named searches: the search page's address parameters |

Migrations: [V1](src/main/resources/db/migration/V1__create_tracker_tables.sql) creates
the tracker, [V2](src/main/resources/db/migration/V2__add_cv_versions_reminders_archive_date.sql)
adds CVs, reminders, the archive date, and the CV used per application, and
[V3](src/main/resources/db/migration/V3__move_reminders_to_reminder_service.sql)
hands existing reminders to the reminder service through the outbox;
[V4](src/main/resources/db/migration/V4__add_skill_profile.sql) adds the skill profile;
[V5](src/main/resources/db/migration/V5__add_saved_searches.sql) adds saved searches.

### CV files

- Only PDF and Word (`.docx`) files up to 5 MB, checked by their first bytes as well
  as their name.
- Stored under a random name in a private folder (`data/documents`, ignored by Git),
  never under the uploaded file name.
- Served only to their owner, always as a download with `nosniff`, never inline.
- The [DocumentStorage](src/main/java/com/jobtrack/documents/DocumentStorage.java)
  interface lets private Azure Blob Storage replace the local folder later.

### CV skill matching

From **Your CVs → Use for matching** (or the **Your skills** page), JobTrack reads the
CV's text in memory with Apache PDFBox (PDF) or the JDK's zip reader (Word), finds
known skills with [SkillCatalog](src/main/java/com/jobtrack/matching/SkillCatalog.java),
and stores only the skill names and how often each appeared. You can remove skills
and add your own.

On the search page each result shows **Strong fit**, **Some fit**, **Low fit** or
**Not enough detail**, with the skills you have and the ones you are missing, based
on HiringCafe's requirements summary for that job. Results can be sorted by fit.
There is no AI model: the matching is plain text rules, so every result can be
explained. "Java" does not match "JavaScript", and words like "spring" or "rest" only
count in a technical phrase.

### Progress, follow-ups and the board

- **Progress** (`/progress`) works everything out from the application histories: how
  many jobs reached each stage, applications and replies per week for the last 8
  weeks (with a table view), how many applications got an answer and how long that
  took on average.
- **Follow-ups**: an open application (Applied, Interview or Offer) with no stage
  change or note for 14 days is listed on the dashboard and the progress page. "I
  followed up today" adds a note, which starts the 14 days again.
- **Board** (`/board`): one column per open stage, the longest-waiting first, with
  buttons to move each application on. Moves go through the same stage rules.

### Search filters and saved searches

The search form offers most of HiringCafe's `search_jobs` filters: several
workplaces and seniorities at once, posting age, employment type, people management,
years of experience, minimum pay (HiringCafe compares in US dollars), field,
distance, languages, company country, size, type and industries, hidden companies,
perks, no degree, quick applications and jobs per company. Keywords are optional
once a filter is set.

A search is a plain address (`/search?keywords=java&workplace=REMOTE&posted=7`), so
it can be bookmarked or saved under a name and run again from the top of the page.

`search_jobs` has no page or offset input, so **Next** repeats the search with
`exclude_company_names` set to the companies already shown. The page links carry
those names and how many each page added, which lets **Previous** drop exactly the
last page. HiringCafe returns structured cards for about 8 jobs per request.

### Reminders

Reminders live in a separate [reminder service](../reminder-service/README.md) with
its own database. The tracker records each change and an event in one transaction
(the transactional outbox), a publisher sends events to a queue, and the dashboard
reads reminders back over an internal API. A change shows up a second or two later;
until then the page says it is on its way. How duplicates, retries and
out-of-order messages are handled is in [docs/events.md](../docs/events.md).

Reminders are entered in the configured time zone and stored as instants. Nothing
is sent by email or text message.

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
| `GET /api/cv-versions`, `POST /api/cv-versions` | List, or upload as multipart `label` + `file` |
| `GET /api/cv-versions/{id}/file` | Download a CV |
| `PUT /api/applications/{id}/cv-version` | Record the CV sent: `{"cvVersionId": 3}`, or `null` to clear |
| `GET /api/applications/{id}/reminders`, `POST` same path | List, or add: `{"date": "2026-11-09", "time": "10:00", "note": "…"}`. Adding answers 202 Accepted with the new ID; the reminder appears a moment later |
| `PATCH /api/reminders/{id}`, `POST /api/reminders/{id}/complete` | Move or finish a reminder (202 Accepted) |
| `GET /api/export` | Download all your records as JSON (the page version is `/export`) |

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

The Azure deployment is prepared but not run: see [docs/azure-lab.md](../docs/azure-lab.md).
