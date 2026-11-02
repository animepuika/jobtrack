-- Skills used to compare jobs with the user's CV. Only the skill names are stored;
-- the CV text they were read from is never saved.
create table skill_profile (
    owner_id       bigint       primary key references app_user (id),
    cv_version_id  bigint       references cv_version (id),
    cv_label       varchar(100),
    updated_at     timestamptz  not null default now()
);

create table user_skill (
    owner_id bigint      not null references app_user (id),
    name     varchar(60) not null check (btrim(name) <> ''),
    weight   integer     not null default 1 check (weight > 0),
    source   varchar(10) not null check (source in ('CV', 'MANUAL')),
    primary key (owner_id, name)
);
