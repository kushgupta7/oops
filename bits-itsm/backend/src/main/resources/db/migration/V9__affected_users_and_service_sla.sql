-- V9: number of affected users as a priority factor, and an optional per-service SLA factor.
-- Additive only: existing incidents keep a NULL user count; every service starts at factor 1.0.
alter table incidents add column if not exists affected_users integer check (affected_users is null or affected_users between 1 and 100000);
alter table catalog_entries add column if not exists sla_factor double precision not null default 1.0 check (sla_factor between 0.25 and 4.0);
-- Factor captured on each incident together with its SLA targets (NULL for incidents classified before V8).
alter table incidents add column if not exists sla_factor double precision;
