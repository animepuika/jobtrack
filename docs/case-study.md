# JobTrack case study

JobTrack is a personal job application tracker built between October 31 and
November 2, 2026 as a learning and portfolio project. This document explains what
was built, the decisions behind it, the problems found on the way, and what could
be better. It is written so the project can be explained without notes.

## The problem

Job hunting spreads information across job boards, emails and documents. The goal
was one place to keep selected jobs, where each application stands, interview notes,
which CV was sent, and when to follow up, and to learn a realistic Java stack while
building it.

Constraints: personal use, a public portfolio with fictional data only, no hosting
cost (EUR 0), and no automated applying, mailbox access or paid AI.

## What was built

| Area | What it does |
| --- | --- |
| Tracking | Jobs, applications, stage rules, dated history, notes, archive |
| Pages and API | Thymeleaf pages with form login and CSRF; a stateless JSON API with HTTP Basic |
| HiringCafe | Live search through HiringCafe's MCP server and de-duplicated imports |
| Documents | Private CV uploads with type checks and owner-only downloads |
| Matching | Skills read from the CV, fit shown on each search result |
| Reminders | A separate service, fed by events through a transactional outbox and a queue |
| Export | All of a user's records as JSON |
| Delivery | Docker images, Docker Compose, GitHub Actions running ~150 tests |

## Key decisions

**Start as one modular application, then extract one service.** The tracker began
as a single Spring Boot app with clear packages (tracking, job source, documents,
reminders, export). Reminders were written from the start to refer to applications
by ID only, which made the later extraction into a separate service a contained
change. Trade-off: a monolith first is simpler to build and test; a microservice is
only worth its cost where a real boundary exists.

**Transactional outbox instead of sending to the queue directly.** If the tracker
saved a change and then sent a message, a crash between the two would lose the
message, and sending first could announce a change that never happened. Writing the
event into an `outbox_event` table in the same transaction as the change, and
sending it afterwards, means an event exists if and only if its change does.
Trade-off: delivery becomes at least once, so the receiver must handle duplicates.

**Event IDs and versions on the receiving side.** The reminder service stores each
handled event ID in the same transaction as the event's effect, so a repeat is
ignored. Each event about a reminder has a higher version than the last, so an old
event arriving late cannot move a reminder back or bring a finished one back to
life. These two rules make retries safe.

**Raw JSON-RPC instead of an MCP SDK for HiringCafe.** HiringCafe exposes an MCP
server. An MCP tool call is a plain JSON-RPC request, so the adapter sends it with
Spring's `RestClient`. This kept the dependency small and made the protocol visible.
The adapter hides HiringCafe behind a `JobSource` interface, so the tracker's domain
never sees provider-specific data.

**Rule-based CV matching instead of AI.** The scope ruled out paid AI and sending
personal data to third parties. A skill catalog with careful patterns ("Java" must
not match "JavaScript", "spring" only counts as "Spring Boot" or "Spring framework")
gives results that can always be explained: every fit shows the matched and missing
skills. Trade-off: it only knows listed skills and cannot judge experience levels.

**Server-rendered pages instead of a single-page app.** Thymeleaf keeps Java
central to the project, works without JavaScript, and gets CSRF protection and
session security from Spring Security without extra code. Small optional scripts
add focus handling and loading states.

**Testcontainers for real databases in tests.** Integration tests run against real
PostgreSQL 18 and a real Azurite queue in Docker, not an in-memory imitation, so SQL
features (partial unique indexes, `for update skip locked`) are tested as they run.

## Problems found and fixed

These came up during the build and are good examples of why testing against the
real thing matters.

| Problem | Cause | Fix |
| --- | --- | --- |
| Stage counters showed errors | In Spring's expression language, `counts[s]` reads the literal key "s", not the variable | Use `counts.get(s)` |
| The detail page lost its data | Thymeleaf reserves the name `application` | Renamed the model attribute |
| The data migration would fail with two or more reminders | `gen_random_uuid()` in a `lateral` subquery that does not refer to the outer row runs only once, giving every event the same ID | Generate the ID in a per-row subquery; a migration test now builds the old schema, adds two reminders and runs it |
| A later event could overtake an earlier one | After an event failed and waited for a retry, the next event for the same reminder was still due | The publisher holds back an event while an earlier event for the same reminder is waiting |
| Queue outages were invisible for minutes | The Azure SDK retried internally before failing | One short try per call; retries happen in the outbox, where they are recorded |
| Uploaded CVs could have been committed | Uploads went to `app/data/`, which the root `.gitignore` did not cover | Ignored `app/data/` and checked it with `git check-ignore` |
| Live search failed on the home network | The network's DNS security filter redirected hiringcafe.com to a block page | Diagnosed by comparing DNS answers and certificates; the app reports "could not be reached" and keeps manual entry |
| CI would not have started the build | `mvnw` was committed without its executable bit on Windows | Set the bit in Git |

## How it was tested

- **Unit tests** for stage rules, URL normalization, skill matching and the
  HiringCafe adapter against a local fake HTTP server, including timeouts, HTTP 429
  with `Retry-After`, and errors returned with HTTP 200.
- **Integration tests** against real PostgreSQL for ownership (a user can never see
  another user's records), duplicate prevention, the outbox under failures, and the
  reminder service's duplicate, stale and out-of-order handling.
- **End-to-end checks** with the full Docker Compose stack: stopping the queue and the
  reminder service, and confirming that pages keep working and every event is
  delivered once they are back.
- **CI** runs all of this on every push and builds both images.

## What could be better

- Fit only knows catalogued skills; a skill list per job family and weighting by how
  central a skill is to a job would help.
- Reminders show in the app only; a scheduled cloud job could send a daily summary.
- The public demo has no screenshots or video yet.
- CVs are stored on the local disk; the `DocumentStorage` interface is ready for
  private Azure Blob Storage.
- Images are built in CI but not published; publishing them is needed for the Azure
  deployment.
