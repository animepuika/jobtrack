-- Which search results the user has opened, so later searches can mark them.
-- Only the provider's job ID is kept, not the listing.
create table viewed_job (
    owner_id        bigint       not null references app_user (id),
    source_provider varchar(20)  not null check (source_provider in ('HIRINGCAFE')),
    external_id     varchar(100) not null check (btrim(external_id) <> ''),
    first_viewed_at timestamptz  not null,
    last_viewed_at  timestamptz  not null,
    primary key (owner_id, source_provider, external_id)
);
