create table academic_years (
    id         uuid        primary key default gen_random_uuid(),
    school_id  uuid        not null references schools (id),
    name       varchar(50) not null,
    start_date date        not null,
    end_date   date        not null,
    status     varchar(10) not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    version    bigint      not null default 0,
    constraint ck_academic_years_status check (status in ('PLANNED', 'ACTIVE', 'CLOSED')),
    constraint ck_academic_years_dates check (end_date > start_date)
);

create unique index uq_academic_years_school_name on academic_years (school_id, lower(name));
-- At most one ACTIVE academic year per school, enforced by the database as a last line of defence.
create unique index uq_academic_years_one_active on academic_years (school_id) where status = 'ACTIVE';
create index ix_academic_years_school_start on academic_years (school_id, start_date);

create table grade_levels (
    id            uuid        primary key default gen_random_uuid(),
    school_id     uuid        not null references schools (id),
    name          varchar(50) not null,
    display_order integer     not null default 0,
    active        boolean     not null default true,
    created_at    timestamptz not null default now(),
    updated_at    timestamptz not null default now(),
    version       bigint      not null default 0,
    constraint ck_grade_levels_display_order check (display_order >= 0)
);

create unique index uq_grade_levels_school_name on grade_levels (school_id, lower(name));
create index ix_grade_levels_school_order on grade_levels (school_id, display_order);

create table subjects (
    id          uuid         primary key default gen_random_uuid(),
    school_id   uuid         not null references schools (id),
    code        varchar(20)  not null,
    name        varchar(100) not null,
    description varchar(500),
    active      boolean      not null default true,
    created_at  timestamptz  not null default now(),
    updated_at  timestamptz  not null default now(),
    version     bigint       not null default 0
);

create unique index uq_subjects_school_code on subjects (school_id, lower(code));
create index ix_subjects_school_name on subjects (school_id, lower(name));

-- class_teacher_id and teacher_id reference the teacher's user account; teacher profiles arrive in a later migration.
create table sections (
    id               uuid        primary key default gen_random_uuid(),
    school_id        uuid        not null references schools (id),
    academic_year_id uuid        not null references academic_years (id),
    grade_level_id   uuid        not null references grade_levels (id),
    name             varchar(20) not null,
    capacity         integer     not null,
    class_teacher_id uuid        references users (id),
    active           boolean     not null default true,
    created_at       timestamptz not null default now(),
    updated_at       timestamptz not null default now(),
    version          bigint      not null default 0,
    constraint ck_sections_capacity check (capacity between 1 and 500),
    constraint uq_sections_id_year unique (id, academic_year_id)
);

create unique index uq_sections_year_grade_name on sections (academic_year_id, grade_level_id, lower(name));
create index ix_sections_school_year on sections (school_id, academic_year_id);
create index ix_sections_grade on sections (grade_level_id);
create index ix_sections_class_teacher on sections (class_teacher_id);

create table section_subjects (
    id               uuid        primary key default gen_random_uuid(),
    school_id        uuid        not null references schools (id),
    academic_year_id uuid        not null references academic_years (id),
    section_id       uuid        not null,
    subject_id       uuid        not null references subjects (id),
    teacher_id       uuid        references users (id),
    weekly_periods   integer     not null,
    active           boolean     not null default true,
    created_at       timestamptz not null default now(),
    updated_at       timestamptz not null default now(),
    version          bigint      not null default 0,
    constraint ck_section_subjects_periods check (weekly_periods between 1 and 40),
    constraint uq_section_subjects_section_subject unique (section_id, subject_id),
    -- The academic year must match the section's year.
    constraint fk_section_subjects_section_year foreign key (section_id, academic_year_id)
        references sections (id, academic_year_id)
);

create index ix_section_subjects_school_year on section_subjects (school_id, academic_year_id);
create index ix_section_subjects_subject on section_subjects (subject_id);
create index ix_section_subjects_teacher on section_subjects (teacher_id);
