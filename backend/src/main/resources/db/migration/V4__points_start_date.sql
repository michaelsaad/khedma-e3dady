-- Attendance on meetings before this date never awards points automatically
-- (e.g. history imported from the previous system). Defaults to the season start.
alter table app_settings add column points_start_date date;
update app_settings set points_start_date = season_start;
alter table app_settings alter column points_start_date set not null;
