-- Composite keys let enrollments prove that a section belongs to the stated academic year and grade.
alter table sections add constraint uq_sections_id_year_grade unique (id, academic_year_id, grade_level_id);

create table student_profiles (
    id                      uuid         primary key default gen_random_uuid(),
    user_id                 uuid         not null references users (id),
    school_id               uuid         not null references schools (id),
    admission_number        varchar(30)  not null,
    date_of_birth           date         not null,
    gender                  varchar(10)  not null,
    admission_date          date         not null,
    blood_group             varchar(5),
    address_line1           varchar(200) not null,
    address_line2           varchar(200),
    city                    varchar(100) not null,
    state                   varchar(100),
    postal_code             varchar(20),
    emergency_contact_name  varchar(100) not null,
    emergency_contact_phone varchar(30)  not null,
    profile_photo_key       varchar(255),
    status                  varchar(12)  not null,
    created_at              timestamptz  not null default now(),
    updated_at              timestamptz  not null default now(),
    version                 bigint       not null default 0,
    constraint uq_student_profiles_user unique (user_id),
    constraint ck_student_profiles_gender check (gender in ('MALE', 'FEMALE', 'OTHER')),
    constraint ck_student_profiles_status check (status in ('ACTIVE', 'INACTIVE', 'WITHDRAWN', 'GRADUATED')),
    constraint ck_student_profiles_blood_group
        check (blood_group is null or blood_group in ('A+', 'A-', 'B+', 'B-', 'AB+', 'AB-', 'O+', 'O-')),
    constraint ck_student_profiles_dates check (admission_date >= date_of_birth)
);

create unique index uq_student_profiles_school_admission on student_profiles (school_id, lower(admission_number));
create index ix_student_profiles_school_status on student_profiles (school_id, status);

create table guardians (
    id                uuid         primary key default gen_random_uuid(),
    school_id         uuid         not null references schools (id),
    first_name        varchar(100) not null,
    last_name         varchar(100) not null,
    relationship      varchar(15)  not null,
    email             varchar(254),
    phone_number      varchar(30)  not null,
    occupation        varchar(100),
    address           varchar(500),
    emergency_contact boolean      not null default false,
    created_at        timestamptz  not null default now(),
    updated_at        timestamptz  not null default now(),
    version           bigint       not null default 0,
    constraint ck_guardians_relationship
        check (relationship in ('FATHER', 'MOTHER', 'GUARDIAN', 'GRANDPARENT', 'SIBLING', 'OTHER'))
);

create index ix_guardians_school_last_name on guardians (school_id, lower(last_name));
create index ix_guardians_school_email on guardians (school_id, lower(email));

create table student_guardians (
    id                     uuid        primary key default gen_random_uuid(),
    student_id             uuid        not null references student_profiles (id),
    guardian_id            uuid        not null references guardians (id),
    primary_guardian       boolean     not null default false,
    receives_notifications boolean     not null default true,
    authorized_pickup      boolean     not null default false,
    created_at             timestamptz not null default now(),
    updated_at             timestamptz not null default now(),
    version                bigint      not null default 0,
    constraint uq_student_guardians_pair unique (student_id, guardian_id)
);

-- A student has at most one primary guardian.
create unique index uq_student_guardians_one_primary on student_guardians (student_id) where primary_guardian;
create index ix_student_guardians_guardian on student_guardians (guardian_id);

create table promotion_batches (
    id                      uuid        primary key default gen_random_uuid(),
    school_id               uuid        not null references schools (id),
    source_academic_year_id uuid        not null references academic_years (id),
    target_academic_year_id uuid        not null references academic_years (id),
    performed_by            uuid        not null references users (id),
    performed_at            timestamptz not null default now(),
    total_count             integer     not null,
    promoted_count          integer     not null,
    failed_count            integer     not null,
    created_at              timestamptz not null default now(),
    updated_at              timestamptz not null default now(),
    version                 bigint      not null default 0,
    constraint ck_promotion_batches_counts check (promoted_count + failed_count = total_count)
);

create index ix_promotion_batches_school_time on promotion_batches (school_id, performed_at desc);

-- Enrollment rows are history: promotion and transfer end the old row and add a new one, nothing is overwritten.
create table student_enrollments (
    id                   uuid        primary key default gen_random_uuid(),
    school_id            uuid        not null references schools (id),
    student_id           uuid        not null references student_profiles (id),
    academic_year_id     uuid        not null references academic_years (id),
    grade_level_id       uuid        not null references grade_levels (id),
    section_id           uuid        not null,
    roll_number          integer,
    enrollment_date      date        not null,
    status               varchar(12) not null,
    source_enrollment_id uuid        references student_enrollments (id),
    promotion_batch_id   uuid        references promotion_batches (id),
    created_by           uuid        references users (id),
    created_at           timestamptz not null default now(),
    updated_at           timestamptz not null default now(),
    version              bigint      not null default 0,
    constraint ck_student_enrollments_status
        check (status in ('ACTIVE', 'TRANSFERRED', 'PROMOTED', 'WITHDRAWN', 'COMPLETED')),
    constraint ck_student_enrollments_roll check (roll_number is null or roll_number > 0),
    constraint fk_student_enrollments_section foreign key (section_id, academic_year_id, grade_level_id)
        references sections (id, academic_year_id, grade_level_id)
);

create unique index uq_student_enrollments_one_active_per_year
    on student_enrollments (student_id, academic_year_id) where status = 'ACTIVE';
create unique index uq_student_enrollments_roll_per_section
    on student_enrollments (section_id, roll_number) where status = 'ACTIVE' and roll_number is not null;
create index ix_student_enrollments_section_status on student_enrollments (section_id, status);
create index ix_student_enrollments_student on student_enrollments (student_id);
create index ix_student_enrollments_year_status on student_enrollments (academic_year_id, status);
create index ix_student_enrollments_batch on student_enrollments (promotion_batch_id);
