create table teacher_profiles (
    id                uuid         primary key default gen_random_uuid(),
    user_id           uuid         not null references users (id),
    school_id         uuid         not null references schools (id),
    employee_id       varchar(30)  not null,
    joining_date      date         not null,
    qualification     varchar(200),
    specialization    varchar(200),
    employment_status varchar(10)  not null,
    profile_photo_key varchar(255),
    created_at        timestamptz  not null default now(),
    updated_at        timestamptz  not null default now(),
    version           bigint       not null default 0,
    constraint uq_teacher_profiles_user unique (user_id),
    constraint ck_teacher_profiles_status check (employment_status in ('ACTIVE', 'ON_LEAVE', 'RESIGNED', 'RETIRED'))
);

create unique index uq_teacher_profiles_school_employee on teacher_profiles (school_id, lower(employee_id));
create index ix_teacher_profiles_school_status on teacher_profiles (school_id, employment_status);
