# Claude Design prompt: JobTrack UI and UX

Paste everything below the line into Claude Design. Attach screenshots of the
current pages if you have them (sign in as `demo` / `demo-password` in the demo).

---

Design the user interface and user experience for **JobTrack**, a personal web app
for tracking job applications. One person uses it to keep every job they are
considering in one place, record where each application stands, and remember what
happened. It is also a developer portfolio project, so it should look calm,
credible, and carefully made, not like a template.

## Who uses it and how

- One job seeker, often a junior developer, checking in a few times a week on a
  laptop, sometimes on a phone.
- Typical visit: see what changed, open one application, move it to the next stage
  or add a note after an interview, then leave. Should take under a minute.
- Occasional visit: search for new jobs, save a few, add one found elsewhere.
- Emotional context: job hunting is stressful. Rejections are common. The interface
  should feel organized and encouraging, never gamified or judgmental. Rejected
  and withdrawn applications are normal outcomes, not failures to highlight in red.

## Core concepts the design must make clear

- **Saved is not applied.** A new job starts as *Saved* ("I'm interested").
- **Stages** move only forward along fixed paths:
  `Saved → Applied → Interview → Offer → Accepted`, and from any open stage to
  `Withdrawn` (I stopped) or, after applying, `Rejected` (they stopped).
  Accepted, Rejected and Withdrawn are final. The UI should only ever offer the
  moves that are allowed from the current stage.
- **History** is a permanent timeline: when the job was saved, each stage change
  with its date, and notes. Entries are never edited.
- **Archive** hides an application from the dashboard without deleting it.
- **Source**: a job is either *added by hand* or *saved from HiringCafe*, a job
  search service. Search results are temporary until the user chooses *Track*.

## Screens to design

For each screen, show the default state plus the listed states, on desktop
(1280 px) and mobile (390 px).

1. **Sign in.** Username and password. States: wrong password, signed out.
   There is no sign-up; accounts are created by configuration.

2. **Dashboard** (home).
   - Count per stage, each acting as a filter.
   - List of applications: job title, company, location, stage, last updated.
     Filter by text, stage, and "show archived".
   - Recent activity feed (latest 8 events across all applications).
   - Primary actions: *Add job*, *Search HiringCafe*.
   - States: brand-new user with nothing tracked (onboarding empty state); filters
     with no matches; 40+ applications; archived rows shown.

3. **Application detail.**
   - Title, company, location, current stage, link to the original listing,
     source, applied date.
   - *Move to* control offering only the allowed next stages, with a date (today
     by default, never in the future).
   - Add a note (multi-line text, date).
   - History timeline, newest first, distinguishing *created*, *stage change*
     and *note* entries.
   - Edit job details, archive or restore.
   - States: final stage (no more moves; explain why), archived, after a
     successful change (confirmation), refused change (error message), very long
     note, long job title.

4. **Add or edit job.** Title and company (required), location, link to the
   listing (optional; not editable afterwards because it identifies the job).
   States: validation errors, "you already track this job" with a link to it.

5. **Search HiringCafe.** Keywords (required), location, workplace (Remote,
   Hybrid, Onsite), seniority (No prior experience, Entry, Mid, Senior).
   - Result cards: title linking to the listing, company, up to three short
     highlight chips exactly as the provider sends them (for example
     "Riga, Riga · Hybrid", "30k–40k/yr", "Full Time · Entry Level"), and a
     *Track* button, or *Tracked · open* when it is already saved.
   - Header line: "Showing 10 of 33 matches", and a short note that the
     provider's location filter is broad.
   - States: before searching, loading, results, no results, provider
     unavailable or rate limited ("asked to wait 30 seconds") with a clear route
     to *add the job by hand*, and live search turned off in the demo.

6. **Not found.** An application that does not exist or belongs to someone else.

## Technical constraints (important)

- Pages are rendered on the server with Spring Boot and **Thymeleaf**: plain HTML
  forms that submit and redirect. Design for that. No single-page app, no client
  framework, and every core task must work without JavaScript. Small optional
  enhancements are fine if the page works without them.
- Styling is one hand-written CSS file with custom properties. Deliver design
  tokens (colors, type scale, spacing, radii, shadows) as CSS custom properties
  and component styles that can be written in plain CSS.
- Light and dark themes, following the operating-system setting.
- WCAG 2.2 AA: contrast, visible focus, labels on every field, status messages
  announced to screen readers, never color alone to show a stage, and touch
  targets of at least 44 px on mobile.
- System font stack or one freely licensed font. No icon font; inline SVG icons
  are fine.

## Content rules

- Use only fictional companies and jobs in mockups, such as Example Logistics,
  Sample Health, Northwind Analytics, or Contoso Retail. No real listings.
- Do not use HiringCafe's logo or imitate its branding. Refer to it by name only,
  and do not suggest an official partnership.
- Plain, friendly microcopy. Dates like "2 Nov 2026".

## Out of scope; do not design

Automatic applying, email or calendar integration, AI matching or scores,
recommendations, social features, notifications, payments or subscriptions,
multi-user teams. CV uploads and reminders come later; at most leave room for a
future "Reminders" area on the dashboard and application page.

## What to deliver

1. A short design rationale: layout, hierarchy, and how stages are visualized.
2. Design tokens for light and dark themes.
3. Components: header and navigation, buttons (primary, secondary, subtle,
   destructive-looking-but-safe archive), form fields with errors, stage badge
   (7 stages, distinguishable without color), stage counters, application row
   or card, timeline entries (3 kinds), search result card, chips, flash
   messages (success and error), empty states.
4. All screens and states listed above, desktop and mobile.
5. Interaction notes: focus order, what happens after each form submit, and how
   the mobile dashboard list behaves.
