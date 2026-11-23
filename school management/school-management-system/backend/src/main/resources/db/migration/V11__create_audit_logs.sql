-- Phase 9: persistent, append-only audit trail. Rows are only ever inserted; the application has no update or
-- delete path for them. school_id is null for events that cannot be tied to a school (a sign-in with an unknown
-- account), which no administrator can read through the API.

create table audit_logs (
    id            uuid         primary key default gen_random_uuid(),
    school_id     uuid         references schools (id),
    actor_user_id uuid         references users (id) on delete set null,
    action        varchar(60)  not null,
    entity_type   varchar(60),
    entity_id     varchar(64),
    summary       varchar(500),
    old_values    jsonb,
    new_values    jsonb,
    ip_address    varchar(45),
    user_agent    varchar(255),
    created_at    timestamptz  not null default now()
);

create index ix_audit_logs_school_time on audit_logs (school_id, created_at desc);
create index ix_audit_logs_school_action on audit_logs (school_id, action, created_at desc);
create index ix_audit_logs_entity on audit_logs (school_id, entity_type, entity_id);
create index ix_audit_logs_actor on audit_logs (actor_user_id, created_at desc);
