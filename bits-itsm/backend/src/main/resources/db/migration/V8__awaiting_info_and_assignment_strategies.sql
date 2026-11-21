-- V8: AWAITING_INFO lifecycle state (State pattern) with a paused resolution SLA clock,
-- and three additional runtime-selectable assignment strategies. Additive only: no rows are changed.
alter table incidents drop constraint if exists incidents_status_check;
alter table incidents add constraint incidents_status_check
    check (status in ('NEW','CLASSIFIED','ASSIGNED','ACKNOWLEDGED','IN_PROGRESS','AWAITING_INFO','RESOLVED','VERIFIED','CLOSED'));
alter table incidents add column if not exists sla_paused_at timestamp(6) with time zone;
alter table incidents add column if not exists sla_paused_seconds bigint not null default 0;

alter table assignment_settings drop constraint if exists assignment_settings_strategy_check;
alter table assignment_settings add constraint assignment_settings_strategy_check
    check (strategy in ('SKILL_BASED','LOAD_BASED','ROUND_ROBIN','PRIORITY_BASED','SMART'));

-- Speeds up the auto-close job (RESOLVED/VERIFIED incidents idle beyond the cut-off).
create index if not exists incident_status_updated_idx on incidents (status, updated_at);
