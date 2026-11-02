create table app_user (
    id            bigint generated always as identity primary key,
    username      varchar(50)  not null unique,
    password_hash varchar(100) not null,
    created_at    timestamptz  not null default now()
);

create table tracked_job (
    id              bigint generated always as identity primary key,
    owner_id        bigint        not null references app_user (id),
    title           varchar(200)  not null check (btrim(title) <> ''),
    company         varchar(200)  not null check (btrim(company) <> ''),
    location        varchar(200),
    source_url      varchar(2000),
    source_provider varchar(20)   not null check (source_provider in ('MANUAL', 'HIRINGCAFE')),
    external_id     varchar(100),
    created_at      timestamptz   not null default now(),
    check (source_provider = 'MANUAL' or external_id is not null)
);

-- An imported job is identified by its provider and the provider's job ID.
create unique index tracked_job_external_unique
    on tracked_job (owner_id, source_provider, external_id)
    where external_id is not null;

-- A manually entered job is identified by its normalized source URL, when it has one.
create unique index tracked_job_manual_url_unique
    on tracked_job (owner_id, source_url)
    where source_provider = 'MANUAL' and source_url is not null;

create table job_application (
    id         bigint generated always as identity primary key,
    job_id     bigint      not null unique references tracked_job (id),
    stage      varchar(20) not null
        check (stage in ('SAVED', 'APPLIED', 'INTERVIEW', 'OFFER', 'ACCEPTED', 'REJECTED', 'WITHDRAWN')),
    applied_on date,
    archived   boolean     not null default false,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    version    bigint      not null default 0
);

create table application_event (
    id             bigint generated always as identity primary key,
    application_id bigint      not null references job_application (id),
    type           varchar(20) not null check (type in ('CREATED', 'STAGE_CHANGE', 'NOTE')),
    from_stage     varchar(20),
    to_stage       varchar(20),
    note           text,
    occurred_on    date        not null,
    created_at     timestamptz not null default now(),
    check (type <> 'STAGE_CHANGE' or (from_stage is not null and to_stage is not null)),
    check (type <> 'NOTE' or btrim(note) <> '')
);

create index application_event_application_idx on application_event (application_id, occurred_on, id);
