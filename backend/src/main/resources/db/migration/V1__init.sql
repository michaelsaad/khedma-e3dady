create table student_groups (
    id             bigserial primary key,
    name           varchar(60) not null unique,
    points_balance integer     not null default 0,
    created_at     timestamptz not null default now()
);

create table students (
    id             bigserial primary key,
    name           varchar(100) not null,
    grade          varchar(10)  not null check (grade in ('FIRST', 'SECOND', 'THIRD')),
    phone          varchar(30)  not null default '',
    group_id       bigint references student_groups (id) on delete set null,
    active         boolean      not null default true,
    points_balance integer      not null default 0,
    created_at     timestamptz  not null default now(),
    unique (name, grade)
);

create table app_users (
    id             bigserial primary key,
    email          varchar(255) not null unique,
    password_hash  varchar(100),
    google_subject varchar(255) unique,
    display_name   varchar(150) not null,
    avatar_url     varchar(500),
    role           varchar(20)  not null default 'USER' check (role in ('USER', 'ADMIN', 'SUPER_ADMIN')),
    status         varchar(20)  not null default 'PENDING' check (status in ('PENDING', 'APPROVED', 'REJECTED')),
    -- Optional link so a USER (مخدوم) can see their own points and attendance.
    student_id     bigint unique references students (id) on delete set null,
    created_at     timestamptz  not null default now(),
    last_login_at  timestamptz
);

create table attendance (
    id            bigserial primary key,
    student_id    bigint      not null references students (id) on delete cascade,
    meeting_date  date        not null,
    service       boolean     not null default false,
    mass          boolean     not null default false,
    updated_by_id bigint references app_users (id) on delete set null,
    updated_at    timestamptz not null default now(),
    unique (student_id, meeting_date)
);

create index attendance_meeting_date_idx on attendance (meeting_date);

-- Single ledger for every points movement (students and groups, manual and automatic).
-- The balance on students/student_groups is the cached sum of `amount`.
create table point_transactions (
    id              bigserial primary key,
    student_id      bigint references students (id) on delete cascade,
    group_id        bigint references student_groups (id) on delete cascade,
    type            varchar(30)  not null check (type in ('MANUAL_ADD', 'MANUAL_DEDUCT', 'RESET', 'ATTENDANCE_AWARD', 'ATTENDANCE_REVERSAL')),
    amount          integer      not null,
    balance_after   integer      not null,
    notes           varchar(255) not null,
    attendance_id   bigint references attendance (id) on delete set null,
    attendance_kind varchar(10) check (attendance_kind in ('SERVICE', 'MASS')),
    reverses_id     bigint references point_transactions (id),
    created_by_id   bigint references app_users (id) on delete set null,
    created_at      timestamptz  not null default now(),
    check ((student_id is null) <> (group_id is null))
);

create index point_transactions_student_idx on point_transactions (student_id, id desc);
create index point_transactions_group_idx on point_transactions (group_id, id desc);
create index point_transactions_attendance_idx on point_transactions (attendance_id, attendance_kind);
-- An award can be reversed at most once.
create unique index point_transactions_reverses_uq on point_transactions (reverses_id) where reverses_id is not null;

-- Single-row settings table managed from the "App Settings" page.
create table app_settings (
    id             integer primary key check (id = 1),
    mass_points    integer     not null check (mass_points between 0 and 1000),
    service_points integer     not null check (service_points between 0 and 1000),
    season_start   date        not null,
    season_end     date        not null,
    meeting_day    varchar(10) not null check (meeting_day in ('MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY')),
    updated_by_id  bigint references app_users (id) on delete set null,
    updated_at     timestamptz not null default now(),
    check (season_end > season_start)
);

insert into app_settings (id, mass_points, service_points, season_start, season_end, meeting_day)
values (1, 5, 2, date '2026-09-18', date '2027-09-30', 'FRIDAY');
