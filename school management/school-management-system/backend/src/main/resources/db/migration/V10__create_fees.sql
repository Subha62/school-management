-- Phase 8b: offline fees. Money is numeric(12,2) and payments are never updated or deleted: a mistaken payment is
-- reversed by a separate row, so the full financial history stays.

create table fee_categories (
    id          uuid         primary key default gen_random_uuid(),
    school_id   uuid         not null references schools (id),
    name        varchar(100) not null,
    description varchar(300),
    active      boolean      not null default true,
    created_at  timestamptz  not null default now(),
    updated_at  timestamptz  not null default now(),
    version     bigint       not null default 0
);
create unique index uq_fee_categories_name on fee_categories (school_id, lower(name));

-- What a grade pays for a category in a year, and when. One category can be billed in several instalments.
create table fee_structures (
    id               uuid          primary key default gen_random_uuid(),
    school_id        uuid          not null references schools (id),
    academic_year_id uuid          not null references academic_years (id),
    grade_level_id   uuid          not null references grade_levels (id),
    fee_category_id  uuid          not null references fee_categories (id),
    description      varchar(200),
    amount           numeric(12, 2) not null,
    due_date         date          not null,
    active           boolean       not null default true,
    created_at       timestamptz   not null default now(),
    updated_at       timestamptz   not null default now(),
    version          bigint        not null default 0,
    constraint ck_fee_structures_amount check (amount > 0),
    constraint uq_fee_structures unique (academic_year_id, grade_level_id, fee_category_id, due_date)
);
create index ix_fee_structures_year_grade on fee_structures (school_id, academic_year_id, grade_level_id);

-- A student's bill, copied from the structure when generated so later changes to the structure do not rewrite it.
-- paid_amount is the sum of payments that have not been reversed, kept up to date under a row lock.
create table student_fees (
    id               uuid           primary key default gen_random_uuid(),
    school_id        uuid           not null references schools (id),
    student_id       uuid           not null references student_profiles (id),
    academic_year_id uuid           not null references academic_years (id),
    fee_structure_id uuid           not null references fee_structures (id),
    category_name    varchar(100)   not null,
    description      varchar(200),
    amount           numeric(12, 2) not null,
    paid_amount      numeric(12, 2) not null default 0,
    due_date         date           not null,
    waived           boolean        not null default false,
    waive_reason     varchar(300),
    waived_by        uuid references users (id),
    waived_at        timestamptz,
    created_at       timestamptz    not null default now(),
    updated_at       timestamptz    not null default now(),
    version          bigint         not null default 0,
    constraint uq_student_fees unique (student_id, fee_structure_id),
    constraint ck_student_fees_amount check (amount > 0),
    constraint ck_student_fees_paid check (paid_amount >= 0 and paid_amount <= amount),
    constraint ck_student_fees_waived check (not waived or (paid_amount = 0 and waive_reason is not null))
);
create index ix_student_fees_student on student_fees (student_id, due_date);
create index ix_student_fees_school_year on student_fees (school_id, academic_year_id, due_date);

create table payments (
    id             uuid           primary key default gen_random_uuid(),
    school_id      uuid           not null references schools (id),
    student_fee_id uuid           not null references student_fees (id),
    student_id     uuid           not null references student_profiles (id),
    receipt_number varchar(30)    not null,
    amount         numeric(12, 2) not null,
    method         varchar(15)    not null,
    reference      varchar(100),
    paid_on        date           not null,
    received_by    uuid           not null references users (id),
    created_at     timestamptz    not null default now(),
    updated_at     timestamptz    not null default now(),
    version        bigint         not null default 0,
    constraint uq_payments_receipt unique (school_id, receipt_number),
    constraint ck_payments_amount check (amount > 0),
    constraint ck_payments_method check (method in ('CASH', 'CHEQUE', 'BANK_TRANSFER', 'UPI', 'CARD', 'OTHER'))
);
create index ix_payments_fee on payments (student_fee_id);
create index ix_payments_student on payments (student_id, paid_on desc);
create index ix_payments_school_date on payments (school_id, paid_on desc);

-- A payment can be reversed once; the original row is untouched.
create table payment_reversals (
    id          uuid         primary key default gen_random_uuid(),
    payment_id  uuid         not null references payments (id),
    reason      varchar(300) not null,
    reversed_by uuid         not null references users (id),
    reversed_at timestamptz  not null default now(),
    created_at  timestamptz  not null default now(),
    updated_at  timestamptz  not null default now(),
    version     bigint       not null default 0,
    constraint uq_payment_reversals unique (payment_id)
);

-- Gap-free receipt numbers per school and year; a row is locked while a receipt number is issued.
create table receipt_counters (
    school_id    uuid    not null references schools (id),
    receipt_year integer not null,
    last_number  integer not null default 0,
    primary key (school_id, receipt_year)
);
