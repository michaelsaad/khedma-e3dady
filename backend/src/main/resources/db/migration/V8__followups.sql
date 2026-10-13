-- Follow-up (افتقاد) reports: a servant notes how a student is doing; visible to the author and admins only.
create table followup_types (
    id         bigserial primary key,
    label      varchar(100) not null unique,
    sort_order integer      not null default 0,
    active     boolean      not null default true,
    created_at timestamptz  not null default now()
);

insert into followup_types (label, sort_order) values
    ('تعبان', 1),
    ('عمل شقاوة', 2),
    ('محتاج افتقاد', 3),
    ('غايب ومحتاج اتصال', 4),
    ('ظروف عائلية', 5),
    ('تحسّن ومبسوط', 6);

create table followups (
    id             bigserial primary key,
    student_id     bigint       not null references students (id),
    type_id        bigint       references followup_types (id) on delete set null,
    -- Kept as text so a report still reads right after its type is renamed or removed.
    type_label     varchar(100) not null,
    note           varchar(500) not null default '',
    author_id      bigint       references app_users (id) on delete set null,
    status         varchar(10)  not null default 'OPEN' check (status in ('OPEN', 'DONE')),
    resolved_by_id bigint       references app_users (id) on delete set null,
    resolved_note  varchar(500) not null default '',
    resolved_at    timestamptz,
    created_at     timestamptz  not null default now()
);

create index followups_student_idx on followups (student_id, created_at desc);
create index followups_status_idx on followups (status, created_at desc);
create index followups_author_idx on followups (author_id, created_at desc);
