# Milestone 0 notes: toolchain and HiringCafe feasibility

Checked on November 1, 2026. Each item says whether it was **verified** (run and
observed), **blocked**, or still **open**.

## Toolchain

| Tool | Result | Status |
| --- | --- | --- |
| JDK | **Temurin 25.0.5+8 LTS**, installed with winget (`EclipseAdoptium.Temurin.25.JDK`, hash verified) to `C:\Program Files\Eclipse Adoptium\jdk-25.0.5.8-hotspot` | Verified |
| `java` / `javac` on `PATH` | Both 25.0.5. The installer put the JDK first on the system `PATH`, ahead of Oracle's Java 8 shim | Verified in a fresh environment |
| `JAVA_HOME` | System variable, points to the Temurin 25 JDK | Verified |
| Older Javas | Temurin 21.0.8 and Oracle Java 8 (1.8.0_511) are still installed, but are no longer what `java` resolves to | Left in place |
| Git | 2.47.1, user name configured | Verified |
| Docker CLI / Compose | Docker 28.4.0, Compose v2.39.2 (Docker Desktop 4.46.0) | Verified |
| Docker engine | Docker Desktop crashes during startup, so no engine is available | Blocked |
| PostgreSQL container | Not attempted; needs the Docker engine | Open |

### The Java version mismatch (fixed)

Before Java 25 was installed, `javac` was Temurin 21 but `java` on `PATH` was
Oracle's Java 8 shim. A small `Hello.java` compiled with `javac` 21, and running it
with that `java` failed:

```text
UnsupportedClassVersionError: Hello has been compiled by a more recent version of the
Java Runtime (class file version 65.0), this version of the Java Runtime only
recognizes class file versions up to 52.0
```

Class file version 65 is Java 21; 52 is Java 8. A JVM cannot run bytecode produced
for a newer Java release. Running the same class with Temurin 21's `java` printed
`Hello from Java 21.0.8`.

After installing Temurin 25, a fresh environment compiled the same file (class file
version 69 = Java 25) and printed `Hello from Java 25.0.5`. Terminals and editors
that were already open keep the old `PATH` until they are restarted.

### Docker Desktop startup failure

`%LOCALAPPDATA%\Docker\log\host\com.docker.backend.exe.log` shows:

```text
starting services: initializing Inference manager: listening on
unix://<HOME>\AppData\Local\Docker\run\dockerInference: remove
<HOME>\AppData\Local\Docker\run\dockerInference: The file cannot be accessed by the system.
```

`%LOCALAPPDATA%\Docker\run` contains two 0-byte Unix socket stubs
(`dockerInference`, `userAnalyticsOtlpHttp.sock`) dated September 16, 2026, left by
an earlier run. Windows refuses to delete them, so Docker's AI inference feature
(Model Runner, `EnableDockerAI: true`) cannot start, and Docker Desktop exits.
JobTrack does not need that feature.

## HiringCafe MCP feasibility check

Five requests in total were sent directly to `https://hiringcafe.com/api/mcp`, using
`curl` and JSON-RPC: no SDK and no AI model.

| Check | Result |
| --- | --- |
| `initialize` without credentials | HTTP 200, `application/json`. Server `hiringcafe` 1.0.0, protocol `2025-06-18`. Capabilities: tools, resources. No `Mcp-Session-Id` header returned. |
| `tools/list` | `search_jobs` (about 70 optional filters, none required, including `search`, `location`, `workplace_types`, `seniority_levels`, `employment_types`, `posted_within_days`, `limit`) and `get_job_details` (required `job_id`). |
| `search_jobs` (`"Java developer"`, `"Latvia"`, Entry/Mid Level, `limit: 3`) | HTTP 200 in 0.8 s. Returned 3 of 33 matches. |
| `get_job_details` (valid ID) | HTTP 200 in 0.7 s. Markdown only. |
| `get_job_details` (unknown ID) | **HTTP 200** with `isError: true` and a text message. |
| Rate-limit headers | No `Retry-After` or rate-limit headers on any response. Limits remain undocumented here. |

### Response shapes

`search_jobs` returns a markdown `text` item and structured cards in
`result._meta["hiringcafe.com/job-cards"]`:

```json
{
  "jobs": [
    {
      "id": "abc123fictional",
      "title": "Junior Java Developer",
      "company": "Example Ltd",
      "logo": "https://example.com/favicon.png",
      "meta": ["Riga · Hybrid", "30k–40k/yr", "Full Time · Entry Level"],
      "url": "https://hiringcafe.com/job/junior-java-developer-abc123fictional?utm_source=claude&utm_medium=llm-app&utm_campaign=mcp"
    }
  ],
  "shown": 1, "returned": 1, "total": 1
}
```

The example values are fictional; the field names are the observed ones. The
`meta` strings are display text, not separate fields. The markdown version has
labelled lines (Location, Workplace, Compensation, Employment type, Seniority,
Posted, Job ID) and a short summary.

`get_job_details` returns only markdown: a linked title, labelled fields
(Category, Compensation, Employment type, Job ID, Location, Posted, Role type,
Seniority, Technical tools, Workplace, Years of experience, View & apply), then
"Requirements summary" and "Full description" sections.

### Consequences for the adapter

- The search can map structured cards (`id`, `title`, `company`, `url`) without
  parsing markdown. Location, workplace, and salary are only in display strings or
  markdown, so treat them as optional, best-effort fields.
- Check the MCP `isError` flag, not only the HTTP status.
- Use the job `id` as the duplicate key. Returned URLs carry `utm_*` tracking
  parameters, so the URL is a poor identity for imported jobs.
- The `location` filter is broad: a Latvia search also returned remote roles based
  in Greece and across Europe. Do not assume results are inside the requested place.
- `_meta` is a provider extension and could change; keep a fallback to the text.

### Documentation recheck

The documentation page returns HTTP 403 to non-browser clients and shows a
Cloudflare verification page in the built-in browser. It was not rechecked;
the live calls above are the current evidence. Review the page, and the terms,
in an ordinary browser before Milestone 5.

## Remaining to finish Milestone 0

1. Fix Docker Desktop startup, then start PostgreSQL in a throwaway container and
   run a query.
2. Add the Maven Wrapper when the first Maven project is created (Milestone 1 or 2).
