-- The reminder service's own database. The tracker never reads these tables; it
-- sends events through a queue and reads reminders through the internal API.

create table reminder (
    id             uuid         primary key,
    owner_id       bigint       not null,
    application_id bigint       not null,
    due_at         timestamptz,
    note           varchar(500),
    status         varchar(20)  not null check (status in ('OPEN', 'DONE', 'CANCELLED')),
    version        integer      not null check (version > 0),
    completed_at   timestamptz,
    updated_at     timestamptz  not null
);

create index reminder_owner_open_idx on reminder (owner_id, due_at) where status = 'OPEN';
create index reminder_owner_application_idx on reminder (owner_id, application_id);

-- Every event ID handled so far, so a redelivered message has no second effect.
create table processed_event (
    event_id     uuid        primary key,
    reminder_id  uuid        not null,
    outcome      varchar(20) not null,
    processed_at timestamptz not null
);
