create table timetable_entries (
    id               uuid         primary key default gen_random_uuid(),
    school_id        uuid         not null references schools (id),
    academic_year_id uuid         not null,
    section_id       uuid         not null,
    subject_id       uuid         not null references subjects (id),
    teacher_id       uuid         not null references users (id),
    room             varchar(30),
    day_of_week      varchar(9)   not null,
    period_number    integer      not null,
    start_time       time         not null,
    end_time         time         not null,
    effective_from   date         not null,
    effective_to     date         not null,
    status           varchar(10)  not null default 'ACTIVE',
    created_at       timestamptz  not null default now(),
    updated_at       timestamptz  not null default now(),
    version          bigint       not null default 0,
    constraint fk_timetable_entries_section foreign key (section_id, academic_year_id)
        references sections (id, academic_year_id),
    constraint ck_timetable_entries_day check (day_of_week in
        ('MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY')),
    constraint ck_timetable_entries_period check (period_number between 1 and 20),
    constraint ck_timetable_entries_times check (end_time > start_time),
    constraint ck_timetable_entries_dates check (effective_to >= effective_from),
    constraint ck_timetable_entries_status check (status in ('ACTIVE', 'INACTIVE'))
);

create index ix_timetable_entries_section_day on timetable_entries (section_id, day_of_week);
create index ix_timetable_entries_teacher_day on timetable_entries (teacher_id, day_of_week);
create index ix_timetable_entries_school_year on timetable_entries (school_id, academic_year_id);
create index ix_timetable_entries_room_day on timetable_entries (school_id, lower(room), day_of_week)
    where room is not null;
