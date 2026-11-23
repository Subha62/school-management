-- Phase 7: examinations, marks, grade scale and published results.

-- A grade band is an inclusive percentage range with two decimals. Bands of one school must not overlap; that
-- cannot be a plain constraint without an extension, so the service checks it under a per-school advisory lock.
create table grade_rules (
    id          uuid          primary key default gen_random_uuid(),
    school_id   uuid          not null references schools (id),
    grade       varchar(10)   not null,
    description varchar(100),
    min_percent numeric(5, 2) not null,
    max_percent numeric(5, 2) not null,
    created_at  timestamptz   not null default now(),
    updated_at  timestamptz   not null default now(),
    version     bigint        not null default 0,
    constraint ck_grade_rules_range check (min_percent >= 0 and max_percent <= 100 and min_percent <= max_percent)
);
create unique index uq_grade_rules_school_grade on grade_rules (school_id, lower(grade));

create table exams (
    id               uuid         primary key default gen_random_uuid(),
    school_id        uuid         not null references schools (id),
    academic_year_id uuid         not null references academic_years (id),
    grade_level_id   uuid         not null references grade_levels (id),
    name             varchar(100) not null,
    start_date       date,
    end_date         date,
    status           varchar(20)  not null default 'DRAFT',
    locked_at        timestamptz,
    locked_by        uuid references users (id),
    published_at     timestamptz,
    published_by     uuid references users (id),
    created_at       timestamptz  not null default now(),
    updated_at       timestamptz  not null default now(),
    version          bigint       not null default 0,
    constraint ck_exams_status check (status in ('DRAFT', 'OPEN_FOR_MARKS', 'LOCKED', 'PUBLISHED')),
    constraint ck_exams_dates check (start_date is null or end_date is null or end_date >= start_date),
    constraint ck_exams_published check (status <> 'PUBLISHED' or published_at is not null)
);
create unique index uq_exams_name on exams (school_id, academic_year_id, grade_level_id, lower(name));
create index ix_exams_year_grade on exams (school_id, academic_year_id, grade_level_id, status);

create table exam_subjects (
    id         uuid          primary key default gen_random_uuid(),
    exam_id    uuid          not null references exams (id) on delete cascade,
    subject_id uuid          not null references subjects (id),
    max_marks  numeric(6, 2) not null,
    pass_marks numeric(6, 2) not null,
    exam_date  date,
    created_at timestamptz   not null default now(),
    updated_at timestamptz   not null default now(),
    version    bigint        not null default 0,
    constraint uq_exam_subjects unique (exam_id, subject_id),
    constraint ck_exam_subjects_max check (max_marks > 0 and max_marks <= 1000),
    constraint ck_exam_subjects_pass check (pass_marks >= 0 and pass_marks <= max_marks)
);
create index ix_exam_subjects_subject on exam_subjects (subject_id);

-- One row per student and exam subject once something is entered; absent rows carry no marks.
create table student_marks (
    id              uuid          primary key default gen_random_uuid(),
    exam_subject_id uuid          not null references exam_subjects (id) on delete cascade,
    student_id      uuid          not null references student_profiles (id),
    section_id      uuid          not null references sections (id),
    marks           numeric(6, 2),
    absent          boolean       not null default false,
    remarks         varchar(200),
    entered_by      uuid          not null references users (id),
    created_at      timestamptz   not null default now(),
    updated_at      timestamptz   not null default now(),
    version         bigint        not null default 0,
    constraint uq_student_marks unique (exam_subject_id, student_id),
    constraint ck_student_marks_value check ((absent and marks is null) or (not absent and marks is not null and marks >= 0))
);
create index ix_student_marks_section on student_marks (section_id, exam_subject_id);
create index ix_student_marks_student on student_marks (student_id);

create table exam_comments (
    id         uuid         primary key default gen_random_uuid(),
    exam_id    uuid         not null references exams (id) on delete cascade,
    student_id uuid         not null references student_profiles (id),
    section_id uuid         not null references sections (id),
    comment    varchar(500) not null,
    written_by uuid         not null references users (id),
    created_at timestamptz  not null default now(),
    updated_at timestamptz  not null default now(),
    version    bigint       not null default 0,
    constraint uq_exam_comments unique (exam_id, student_id)
);

-- Frozen when an exam is published, so later changes to the grade scale or to names cannot alter a report card.
create table exam_results (
    id            uuid          primary key default gen_random_uuid(),
    exam_id       uuid          not null references exams (id) on delete cascade,
    student_id    uuid          not null references student_profiles (id),
    section_id    uuid          not null references sections (id),
    section_name  varchar(120)  not null,
    roll_number   integer,
    total_marks   numeric(9, 2) not null,
    total_max     numeric(9, 2) not null,
    percentage    numeric(5, 2) not null,
    grade         varchar(10)   not null,
    result        varchar(4)    not null,
    comment       varchar(500),
    subjects      jsonb         not null,
    created_at    timestamptz   not null default now(),
    updated_at    timestamptz   not null default now(),
    version       bigint        not null default 0,
    constraint uq_exam_results unique (exam_id, student_id),
    constraint ck_exam_results_result check (result in ('PASS', 'FAIL')),
    constraint ck_exam_results_percentage check (percentage >= 0 and percentage <= 100)
);
create index ix_exam_results_student on exam_results (student_id);
create index ix_exam_results_section on exam_results (exam_id, section_id);
