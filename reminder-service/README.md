# JobTrack reminder service

A small Spring Boot service that owns JobTrack's follow-up reminders. It has its
own PostgreSQL database, learns about changes from the tracker's events on a queue,
and answers the tracker's read requests on an internal API. Users never call it
directly. This is milestone 9 of the [project plan](../docs/project-plan.md).

How the two services talk, and how duplicates, retries and out-of-order messages
are handled, is in [docs/events.md](../docs/events.md).

## Run it

The easiest way is from `app/`: `docker compose up -d --build` starts this service
with its database and Azurite. To run it from this folder instead, start those two
first, then:

```bash
./mvnw spring-boot:run
```

| Environment variable | Default | Meaning |
| --- | --- | --- |
| `REMINDERS_DB_URL` | `jdbc:postgresql://localhost:5433/reminders` | Its own database |
| `REMINDERS_DB_USERNAME` / `REMINDERS_DB_PASSWORD` | `reminders` / `reminders` | Database login (local container only) |
| `REMINDERS_QUEUE_CONNECTION_STRING` | local Azurite | Queue the tracker sends events to |
| `REMINDERS_QUEUE_ENABLED` | `true` | Poll the queue every second |
| `REMINDERS_INTERNAL_TOKEN` | `local-development-token` | Bearer token the tracker must send; set a long random value outside your PC |
| `PORT` | `8081` | HTTP port |

## Internal API

`GET /internal/reminders?ownerId=1&applicationId=7&status=OPEN&limit=20` with
`Authorization: Bearer <token>` returns reminders, soonest first. Without the right
token it answers 401.

## How it is organized

```text
com.jobtrack.reminderservice
├── reminder   Reminder entity, event handler (duplicates, versions), queries
├── queue      queue interface, Azure Storage Queue client, polling consumer
└── api        internal API and its token filter
```

## Test it

```bash
./mvnw test
```

16 tests: the event handler against a real PostgreSQL (duplicates, stale and
out-of-order events, cancellation, ownership), the consumer's retry and poison
handling, the internal API's token check, and the queue client against a real
Azurite container.
