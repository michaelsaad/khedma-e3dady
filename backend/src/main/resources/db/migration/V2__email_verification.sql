alter table app_users
    add column email_verified boolean not null default false;

-- One active one-time code per account; a new code replaces the previous one.
create table email_verification_codes (
    user_id      bigint primary key references app_users (id) on delete cascade,
    code_hash    varchar(100) not null,
    expires_at   timestamptz  not null,
    attempts     integer      not null default 0,
    last_sent_at timestamptz  not null
);
