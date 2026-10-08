-- Saved reasons for manual point transactions, picked from a list instead of typed each time.
create table point_reasons (
    id             bigserial primary key,
    label          varchar(200) not null unique,
    -- Optional amount pre-filled when the reason is picked.
    default_amount integer check (default_amount between 1 and 10000),
    usage_count    integer      not null default 0,
    created_at     timestamptz  not null default now()
);
