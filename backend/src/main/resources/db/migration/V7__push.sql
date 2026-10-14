-- Web Push: one row per browser/device an account turned notifications on for.
create table push_subscriptions (
    id              bigserial primary key,
    user_id         bigint       not null references app_users (id) on delete cascade,
    -- The push service URL for this device; it is the identity of the subscription.
    endpoint        varchar(1000) not null unique,
    p256dh          varchar(200) not null,
    auth            varchar(100) not null,
    user_agent      varchar(300) not null default '',
    created_at      timestamptz  not null default now(),
    last_success_at timestamptz
);

create index push_subscriptions_user_idx on push_subscriptions (user_id);

-- Remembers which scheduled notification already ran (e.g. 'friday-reminder:2026-10-16'),
-- so a restart or a second instance can't send it twice.
create table push_runs (
    run_key varchar(100) primary key,
    ran_at  timestamptz not null default now()
);

-- Messages a super admin sent to many people, for the history list.
create table broadcasts (
    id         bigserial primary key,
    title      varchar(100) not null,
    body       varchar(500) not null,
    audience   varchar(20)  not null check (audience in ('ALL', 'ADMINS')),
    sent_by_id bigint       references app_users (id) on delete set null,
    devices    integer      not null,
    created_at timestamptz  not null default now()
);
