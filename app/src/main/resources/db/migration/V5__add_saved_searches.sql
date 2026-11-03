-- Searches the user saved under a name. The query is the search page's address
-- parameters, so running one is opening /search with them.
create table saved_search (
    id         bigint generated always as identity primary key,
    owner_id   bigint        not null references app_user (id),
    name       varchar(60)   not null check (btrim(name) <> ''),
    query      varchar(4000) not null check (btrim(query) <> ''),
    created_at timestamptz   not null default now()
);

create unique index saved_search_name_unique on saved_search (owner_id, lower(name));
