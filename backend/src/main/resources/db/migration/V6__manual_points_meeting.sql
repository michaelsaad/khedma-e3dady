-- Manual points given from the attendance page are tagged with that Friday,
-- so the page can show each student's extra points for the meeting.
alter table point_transactions add column meeting_date date;

create index point_transactions_student_meeting_idx
    on point_transactions (student_id, meeting_date)
    where meeting_date is not null;
