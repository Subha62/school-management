create table attendance_sessions (
    id               uuid         primary key default gen_random_uuid(),
    school_id        uuid         not null references schools (id),
    academic_year_id uuid         not null,
    section_id       uuid         not null,
    attendance_date  date         not null,
    session_type     varchar(10)  not null,
    subject_id       uuid         references subjects (id),
    period_number    integer,
    status           varchar(10)  not null default 'OPEN',
    created_by       uuid         not null references users (id),
    finalized_by     uuid         references users (id),
    finalized_at     timestamptz,
    created_at       timestamptz  not null default now(),
    updated_at       timestamptz  not null default now(),
    version          bigint       not null default 0,
    -- The year is part of the key, so a session can never name a year other than its section's.
    constraint fk_attendance_sessions_section foreign key (section_id, academic_year_id)
        references sections (id, academic_year_id),
    constraint ck_attendance_sessions_type check (session_type in ('DAILY', 'SUBJECT')),
    constraint ck_attendance_sessions_status check (status in ('OPEN', 'FINALIZED')),
    constraint ck_attendance_sessions_subject check (
        (session_type = 'DAILY' and subject_id is null and period_number is null)
        or (session_type = 'SUBJECT' and subject_id is not null)),
    constraint ck_attendance_sessions_period check (period_number is null or period_number between 1 and 20),
    constraint ck_attendance_sessions_finalized check (
        (status = 'FINALIZED' and finalized_at is not null and finalized_by is not null)
        or (status = 'OPEN' and finalized_at is null and finalized_by is null))
);

-- One daily session per section and date; one subject session per section, date, subject and period.
create unique index uq_attendance_sessions_daily
    on attendance_sessions (section_id, attendance_date) where session_type = 'DAILY';
create unique index uq_attendance_sessions_subject
    on attendance_sessions (section_id, attendance_date, subject_id, coalesce(period_number, 0))
    where session_type = 'SUBJECT';
create index ix_attendance_sessions_school_date on attendance_sessions (school_id, attendance_date);
create index ix_attendance_sessions_section_date on attendance_sessions (section_id, attendance_date);
create index ix_attendance_sessions_created_by on attendance_sessions (created_by, attendance_date);

create table attendance_records (
    id         uuid         primary key default gen_random_uuid(),
    session_id uuid         not null references attendance_sessions (id) on delete cascade,
    student_id uuid         not null references student_profiles (id),
    status     varchar(10)  not null,
    remarks    varchar(200),
    marked_by  uuid         not null references users (id),
    created_at timestamptz  not null default now(),
    updated_at timestamptz  not null default now(),
    version    bigint       not null default 0,
    constraint uq_attendance_records_session_student unique (session_id, student_id),
    constraint ck_attendance_records_status
        check (status in ('PRESENT', 'ABSENT', 'LATE', 'HALF_DAY', 'EXCUSED'))
);

create index ix_attendance_records_student on attendance_records (student_id);

-- Every change to a finalized session: who, why, and the value before and after.
create table attendance_corrections (
    id              uuid         primary key default gen_random_uuid(),
    session_id      uuid         not null references attendance_sessions (id) on delete cascade,
    student_id      uuid         not null references student_profiles (id),
    previous_status varchar(10),
    new_status      varchar(10)  not null,
    reason          varchar(500) not null,
    corrected_by    uuid         not null references users (id),
    created_at      timestamptz  not null default now(),
    updated_at      timestamptz  not null default now(),
    version         bigint       not null default 0,
    constraint ck_attendance_corrections_status
        check (new_status in ('PRESENT', 'ABSENT', 'LATE', 'HALF_DAY', 'EXCUSED')
               and (previous_status is null
                    or previous_status in ('PRESENT', 'ABSENT', 'LATE', 'HALF_DAY', 'EXCUSED')))
);

create index ix_attendance_corrections_session on attendance_corrections (session_id, created_at);
