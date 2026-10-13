-- USER is split into SERVANT (read-only dashboard + follow-up reports) and MAKHDOUM (own page only).
-- Existing USER accounts are servants, so they keep exactly the access they had.
alter table app_users drop constraint if exists app_users_role_check;
update app_users set role = 'SERVANT' where role = 'USER';
alter table app_users
    add constraint app_users_role_check check (role in ('MAKHDOUM', 'SERVANT', 'ADMIN', 'SUPER_ADMIN'));
-- New accounts start with the least access; an admin promotes them when approving.
alter table app_users alter column role set default 'MAKHDOUM';
