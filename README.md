# JobTrack

A personal job application and interview tracker, built to learn Java, Spring Boot,
PostgreSQL, Docker, microservices, and Microsoft Azure.

**Status:** milestones 1–7 are built in [app/](app/README.md): manual tracking,
stage rules and history, a JSON API, a signed-in dashboard, PostgreSQL storage,
HiringCafe search and import, private CV versions, reminders, a JSON export, a
Docker image, and a GitHub Actions pipeline. 115 automated tests pass against a real
PostgreSQL. Milestones 8–10 (temporary Azure deployment, reminder microservice,
portfolio release) are not started. The interface follows a Claude Design handoff
made from [docs/design-prompt.md](docs/design-prompt.md).

The [lessons/](lessons/) folder holds the first two plain-Java lessons.

## Purpose

Keep selected jobs, application stages, interview notes, reminders, and CV versions
in one place. Use the project personally and show its design and implementation
in a public portfolio.

The public repository and portfolio examples will contain fictional data. Actual
applications, CVs, credentials, and database exports stay outside version control.

## Planned workflow

1. Add a job manually, or search HiringCafe through its documented connector.
2. Choose a result to track and review the details before saving it.
3. Record application progress, CV version, notes, and follow-up dates.
4. View upcoming interviews and reminders on a dashboard.
5. Export personal tracker records for backup or migration.

See [the data integration blueprint](docs/data-integration.md) for sources,
relationships, integration limits, and public demo behavior.

See [the project and learning plan](docs/project-plan.md) for scope, estimates,
completion criteria, architecture, Azure labs, and the first lesson.

See [the app README](app/README.md) to run, test, and understand the built
application.

## Learning sequence

Each milestone will include an explanation, a small implementation, a way to run
and inspect it, and an exercise to complete independently.

| Milestone | Build | Learn |
| --- | --- | --- |
| 0 | Verified local toolchain and a HiringCafe feasibility check | Java tools, Git, Docker, external dependency checks |
| 1 | Jobs and application stages in memory | Java objects, enums, collections, business rules |
| 2 | A Spring Boot API | HTTP, JSON, dependency injection, validation, error responses |
| 3 | Persistent tracker records | SQL, PostgreSQL, relationships, migrations, integration tests |
| 4 | Dashboard and login | Thymeleaf, sessions, authentication, record ownership |
| 5 | HiringCafe search and selected-job imports | MCP, external service calls, data mapping, duplicates, timeouts |
| 6 | Private CV attachments, reminders, and export | File storage, access checks, scheduling, time zones |
| 7 | Docker and CI | Reproducible environments, automated builds and tests |
| 8 | Temporary Azure deployment | Infrastructure, identity, logs, cost inspection, resource cleanup |
| 9 | Extract the reminder service | Service boundaries, events, retries, duplicate event handling |
| 10 | Portfolio release | Documentation, diagrams, screenshots, walkthrough recording |

Start with one application containing clear modules. When the reminder module
becomes a separate service, it will own its own storage and receive events rather
than accessing the tracker's database tables.

## Hosting approach

Develop locally first. Use Azure for planned learning and demonstration sessions,
record evidence of the deployment, and remove temporary project resources after
use. A permanently running public service is not a requirement.

The target is no hosting spend. Azure eligibility, resource choices, and measured
usage must be checked before deployment; this blueprint is not a zero-cost guarantee.
