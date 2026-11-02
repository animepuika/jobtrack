-- When an application was archived, so the page can say so.
alter table job_application add column archived_on date;
update job_application set archived_on = (updated_at at time zone 'UTC')::date where archived;

-- Uploaded CV files. The file itself lives in document storage under storage_key;
-- the database never holds a public link to it.
create table cv_version (
    id                bigint generated always as identity primary key,
    owner_id          bigint       not null references app_user (id),
    label             varchar(100) not null check (btrim(label) <> ''),
    original_filename varchar(255) not null,
    content_type      varchar(100) not null,
    size_bytes        bigint       not null check (size_bytes > 0),
    storage_key       varchar(100) not null unique,
    uploaded_at       timestamptz  not null default now()
);

create index cv_version_owner_idx on cv_version (owner_id, uploaded_at desc);

-- The CV version sent with an application, if any.
alter table job_application add column cv_version_id bigint references cv_version (id);

-- Follow-up reminders. They point to an application by ID only, so the reminder
-- module can later move into its own service with its own database.
create table reminder (
    id             bigint generated always as identity primary key,
    owner_id       bigint       not null references app_user (id),
    application_id bigint       not null references job_application (id),
    due_at         timestamptz  not null,
    note           varchar(500) not null check (btrim(note) <> ''),
    completed_at   timestamptz,
    created_at     timestamptz  not null default now(),
    version        bigint       not null default 0
);

create index reminder_owner_open_idx on reminder (owner_id, due_at) where completed_at is null;
create index reminder_application_idx on reminder (application_id);
