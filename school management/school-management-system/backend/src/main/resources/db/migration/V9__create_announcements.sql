-- Phase 8a: announcements and per-user read tracking.

create table announcements (
    id           uuid         primary key default gen_random_uuid(),
    school_id    uuid         not null references schools (id),
    title        varchar(200) not null,
    body         text         not null,
    audience     varchar(10)  not null,
    section_id   uuid         references sections (id),
    status       varchar(10)  not null default 'DRAFT',
    publish_at   timestamptz,
    expires_at   timestamptz,
    published_at timestamptz,
    created_by   uuid         not null references users (id),
    created_at   timestamptz  not null default now(),
    updated_at   timestamptz  not null default now(),
    version      bigint       not null default 0,
    constraint ck_announcements_audience check (audience in ('ALL', 'ADMINS', 'TEACHERS', 'STUDENTS', 'SECTION')),
    constraint ck_announcements_status check (status in ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    -- A class announcement names its class; every other audience must not.
    constraint ck_announcements_section check ((audience = 'SECTION') = (section_id is not null)),
    constraint ck_announcements_window check (expires_at is null or publish_at is null or expires_at > publish_at),
    constraint ck_announcements_published check (status = 'DRAFT' or published_at is not null)
);
create index ix_announcements_feed on announcements (school_id, status, audience);
create index ix_announcements_section on announcements (section_id) where section_id is not null;
create index ix_announcements_author on announcements (created_by, created_at desc);

create table announcement_reads (
    id              uuid        primary key default gen_random_uuid(),
    announcement_id uuid        not null references announcements (id) on delete cascade,
    user_id         uuid        not null references users (id),
    read_at         timestamptz not null default now(),
    created_at      timestamptz not null default now(),
    updated_at      timestamptz not null default now(),
    version         bigint      not null default 0,
    constraint uq_announcement_reads unique (announcement_id, user_id)
);
create index ix_announcement_reads_user on announcement_reads (user_id);
