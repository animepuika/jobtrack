-- Reminders now belong to the reminder service, which has its own database. The
-- tracker keeps only what it needs to send correct events: which reminders exist,
-- whose they are, their latest event version, and whether they are still open.
create table reminder_ref (
    id             uuid        primary key,
    owner_id       bigint      not null references app_user (id),
    application_id bigint      not null references job_application (id),
    version        integer     not null check (version > 0),
    open           boolean     not null,
    created_at     timestamptz not null default now()
);

create index reminder_ref_open_idx on reminder_ref (application_id) where open;

-- Transactional outbox: an event is saved in the same transaction as the change it
-- describes, then a publisher sends it to the queue. If the queue is down, events
-- wait here and are retried; nothing is lost.
create table outbox_event (
    id              uuid         primary key,
    owner_id        bigint       not null,
    event_type      varchar(40)  not null,
    aggregate_id    uuid         not null,
    payload         text         not null,
    created_at      timestamptz  not null default now(),
    published_at    timestamptz,
    attempts        integer      not null default 0,
    next_attempt_at timestamptz  not null default now(),
    last_error      varchar(500)
);

create index outbox_event_pending_idx on outbox_event (next_attempt_at, created_at) where published_at is null;
create index outbox_event_owner_pending_idx on outbox_event (owner_id) where published_at is null;

-- Hand existing reminders to the reminder service through the same path as new
-- ones: one "scheduled" event each, plus a "completed" event for finished ones.
-- gen_random_uuid() sits in the select list of a subquery over reminder, so it runs
-- once per row; a subquery that does not refer to reminder may run only once.
alter table reminder add column new_id uuid not null default gen_random_uuid();

insert into reminder_ref (id, owner_id, application_id, version, open, created_at)
select new_id, owner_id, application_id, case when completed_at is null then 1 else 2 end,
       completed_at is null, created_at
from reminder;

insert into outbox_event (id, owner_id, event_type, aggregate_id, payload, created_at)
select r.event_id, r.owner_id, 'REMINDER_SCHEDULED', r.new_id,
       json_build_object(
           'eventId', r.event_id, 'type', 'REMINDER_SCHEDULED',
           'occurredAt', to_char(r.created_at at time zone 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"'),
           'ownerId', r.owner_id, 'applicationId', r.application_id, 'reminderId', r.new_id,
           'version', 1,
           'dueAt', to_char(r.due_at at time zone 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"'),
           'note', r.note)::text,
       r.created_at
from (select gen_random_uuid() as event_id, reminder.* from reminder) r;

insert into outbox_event (id, owner_id, event_type, aggregate_id, payload, created_at)
select r.event_id, r.owner_id, 'REMINDER_COMPLETED', r.new_id,
       json_build_object(
           'eventId', r.event_id, 'type', 'REMINDER_COMPLETED',
           'occurredAt', to_char(r.completed_at at time zone 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"'),
           'ownerId', r.owner_id, 'applicationId', r.application_id, 'reminderId', r.new_id,
           'version', 2)::text,
       r.completed_at
from (select gen_random_uuid() as event_id, reminder.* from reminder where completed_at is not null) r;

drop table reminder;
