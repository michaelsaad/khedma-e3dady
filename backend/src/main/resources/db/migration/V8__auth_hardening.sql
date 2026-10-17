-- Bumping this invalidates every access token already issued to the account (password reset, sign out everywhere).
alter table app_users
    add column token_version integer not null default 0;

-- Codes remember what they were sent for, and the row keeps per-account counters across new codes,
-- so asking for a fresh code no longer resets the number of guesses an attacker gets.
alter table email_verification_codes
    add column purpose             varchar(20) not null default 'VERIFY_EMAIL',
    add column send_window_start   timestamptz,
    add column sends_in_window     integer     not null default 0,
    add column fail_window_start   timestamptz,
    add column failures_in_window  integer     not null default 0;

-- Browsers that already passed the emailed sign-in code; they skip it until expires_at.
create table trusted_devices (
    id           bigserial    primary key,
    user_id      bigint       not null references app_users (id) on delete cascade,
    token_hash   varchar(64)  not null unique,
    user_agent   varchar(255),
    created_at   timestamptz  not null,
    last_used_at timestamptz  not null,
    expires_at   timestamptz  not null
);

create index trusted_devices_user_idx on trusted_devices (user_id);
