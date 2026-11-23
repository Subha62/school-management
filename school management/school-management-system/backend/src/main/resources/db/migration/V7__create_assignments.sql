create table assignments (
    id                   uuid          primary key default gen_random_uuid(),
    school_id            uuid          not null references schools (id),
    academic_year_id     uuid          not null,
    section_id           uuid          not null,
    subject_id           uuid          not null references subjects (id),
    teacher_id           uuid          not null references users (id),
    title                varchar(200)  not null,
    description          text,
    due_at               timestamptz   not null,
    max_marks            numeric(6, 2) not null,
    allow_late           boolean       not null default false,
    status               varchar(10)   not null default 'DRAFT',
    published_at         timestamptz,
    closed_at            timestamptz,
    created_at           timestamptz   not null default now(),
    updated_at           timestamptz   not null default now(),
    version              bigint        not null default 0,
    constraint fk_assignments_section foreign key (section_id, academic_year_id)
        references sections (id, academic_year_id),
    constraint ck_assignments_status check (status in ('DRAFT', 'PUBLISHED', 'CLOSED', 'ARCHIVED')),
    constraint ck_assignments_max_marks check (max_marks > 0 and max_marks <= 1000),
    constraint ck_assignments_published check (status = 'DRAFT' or published_at is not null)
);

create index ix_assignments_teacher on assignments (teacher_id, status, due_at desc);
create index ix_assignments_section on assignments (section_id, status, due_at);
create index ix_assignments_school_year on assignments (school_id, academic_year_id);

-- Files of both kinds share a shape; storage_key is the server-generated name in the file store.
create table assignment_attachments (
    id            uuid          primary key default gen_random_uuid(),
    assignment_id uuid          not null references assignments (id) on delete cascade,
    original_name varchar(150)  not null,
    storage_key   varchar(80)   not null,
    content_type  varchar(100)  not null,
    size_bytes    bigint        not null,
    uploaded_by   uuid          not null references users (id),
    created_at    timestamptz   not null default now(),
    updated_at    timestamptz   not null default now(),
    version       bigint        not null default 0,
    constraint uq_assignment_attachments_key unique (storage_key),
    constraint ck_assignment_attachments_size check (size_bytes > 0)
);

create index ix_assignment_attachments_assignment on assignment_attachments (assignment_id);

create table assignment_submissions (
    id            uuid          primary key default gen_random_uuid(),
    assignment_id uuid          not null references assignments (id),
    student_id    uuid          not null references student_profiles (id),
    status        varchar(25)   not null,
    text_answer   text,
    submitted_at  timestamptz   not null,
    marks         numeric(6, 2),
    feedback      text,
    reviewed_by   uuid          references users (id),
    reviewed_at   timestamptz,
    created_at    timestamptz   not null default now(),
    updated_at    timestamptz   not null default now(),
    version       bigint        not null default 0,
    constraint uq_assignment_submissions_pair unique (assignment_id, student_id),
    constraint ck_assignment_submissions_status
        check (status in ('SUBMITTED', 'LATE', 'REVIEWED', 'RESUBMISSION_REQUESTED')),
    constraint ck_assignment_submissions_marks check (marks is null or marks >= 0),
    -- Marks only exist on a reviewed submission, and a review always names its reviewer.
    constraint ck_assignment_submissions_review check (
        (status = 'REVIEWED' and marks is not null and reviewed_by is not null and reviewed_at is not null)
        or (status <> 'REVIEWED' and marks is null))
);

create index ix_assignment_submissions_student on assignment_submissions (student_id, submitted_at desc);
create index ix_assignment_submissions_status on assignment_submissions (assignment_id, status);

create table submission_attachments (
    id            uuid          primary key default gen_random_uuid(),
    submission_id uuid          not null references assignment_submissions (id) on delete cascade,
    original_name varchar(150)  not null,
    storage_key   varchar(80)   not null,
    content_type  varchar(100)  not null,
    size_bytes    bigint        not null,
    uploaded_by   uuid          not null references users (id),
    created_at    timestamptz   not null default now(),
    updated_at    timestamptz   not null default now(),
    version       bigint        not null default 0,
    constraint uq_submission_attachments_key unique (storage_key),
    constraint ck_submission_attachments_size check (size_bytes > 0)
);

create index ix_submission_attachments_submission on submission_attachments (submission_id);
