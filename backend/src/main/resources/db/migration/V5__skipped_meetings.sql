-- Meetings that didn't happen (e.g. a cancelled Friday). A skipped meeting awards no attendance
-- points (existing awards are reversed) and is left out of attendance rates and meeting counts.
create table skipped_meetings (
    meeting_date  date primary key,
    reason        varchar(200) not null default '',
    created_by_id bigint references app_users (id) on delete set null,
    created_at    timestamptz  not null default now()
);
