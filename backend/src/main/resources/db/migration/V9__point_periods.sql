-- Point periods: points can start again from zero (e.g. after a party) without deleting anything.
-- A period runs from its starts_on until the next period's starts_on. The all-time totals on
-- students and groups (points_balance) are unchanged.

-- The day each transaction counts on: attendance points by their Friday, everything else by the
-- day it was created (Cairo time).
alter table point_transactions add column effective_date date;

update point_transactions t
set effective_date = a.meeting_date
from attendance a
where t.attendance_id = a.id;

update point_transactions
set effective_date = (created_at at time zone 'Africa/Cairo')::date
where effective_date is null;

alter table point_transactions alter column effective_date set not null;

create index point_transactions_student_effective_idx on point_transactions (student_id, effective_date);
create index point_transactions_group_effective_idx on point_transactions (group_id, effective_date);

create table point_periods (
    id            bigserial    primary key,
    name          varchar(100) not null,
    starts_on     date         not null unique,
    created_by_id bigint       references app_users (id) on delete set null,
    created_at    timestamptz  not null default now()
);

-- The first period covers everything recorded so far.
insert into point_periods (name, starts_on)
select 'من البداية',
       least(s.season_start, coalesce((select min(effective_date) from point_transactions), s.season_start))
from app_settings s;

insert into point_periods (name, starts_on)
select 'من البداية', coalesce((select min(effective_date) from point_transactions), current_date)
where not exists (select 1 from point_periods);
