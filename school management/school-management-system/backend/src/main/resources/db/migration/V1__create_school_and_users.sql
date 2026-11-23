create table schools (
    id           uuid         primary key default gen_random_uuid(),
    name         varchar(200) not null,
    school_code  varchar(50)  not null,
    address      varchar(500),
    phone_number varchar(30),
    email        varchar(254),
    timezone     varchar(60)  not null default 'UTC',
    active       boolean      not null default true,
    created_at   timestamptz  not null default now(),
    updated_at   timestamptz  not null default now(),
    version      bigint       not null default 0,
    constraint uq_schools_school_code unique (school_code)
);

create table roles (
    id   uuid        primary key default gen_random_uuid(),
    name varchar(30) not null,
    constraint uq_roles_name unique (name),
    constraint ck_roles_name check (name in ('ROLE_ADMIN', 'ROLE_TEACHER', 'ROLE_STUDENT'))
);

insert into roles (name) values ('ROLE_ADMIN'), ('ROLE_TEACHER'), ('ROLE_STUDENT');

create table users (
    id                    uuid         primary key default gen_random_uuid(),
    school_id             uuid         not null references schools (id),
    username              varchar(100),
    email                 varchar(254) not null,
    password_hash         varchar(100) not null,
    first_name            varchar(100) not null,
    last_name             varchar(100) not null,
    phone_number          varchar(30),
    account_status        varchar(20)  not null,
    email_verified        boolean      not null default false,
    last_login_at         timestamptz,
    failed_login_attempts integer      not null default 0,
    locked_until          timestamptz,
    created_at            timestamptz  not null default now(),
    updated_at            timestamptz  not null default now(),
    version               bigint       not null default 0,
    constraint ck_users_account_status
        check (account_status in ('PENDING', 'ACTIVE', 'LOCKED', 'SUSPENDED', 'ARCHIVED')),
    constraint ck_users_failed_login_attempts check (failed_login_attempts >= 0)
);

create unique index uq_users_school_email on users (school_id, lower(email));
create unique index uq_users_school_username on users (school_id, lower(username)) where username is not null;
create index ix_users_email_lower on users (lower(email));
create index ix_users_username_lower on users (lower(username));
create index ix_users_school_status on users (school_id, account_status);

create table user_roles (
    user_id uuid not null references users (id) on delete cascade,
    role_id uuid not null references roles (id),
    primary key (user_id, role_id)
);

create index ix_user_roles_role on user_roles (role_id);

-- Only SHA-256 hashes of tokens are stored, never the token values.
create table refresh_tokens (
    id         uuid         primary key default gen_random_uuid(),
    user_id    uuid         not null references users (id) on delete cascade,
    family_id  uuid         not null,
    token_hash varchar(64)  not null,
    expires_at timestamptz  not null,
    created_at timestamptz  not null default now(),
    revoked_at timestamptz,
    created_ip varchar(45),
    user_agent varchar(255),
    constraint uq_refresh_tokens_hash unique (token_hash)
);

create index ix_refresh_tokens_user on refresh_tokens (user_id);
create index ix_refresh_tokens_family on refresh_tokens (family_id);
create index ix_refresh_tokens_expires on refresh_tokens (expires_at);

create table password_reset_tokens (
    id         uuid        primary key default gen_random_uuid(),
    user_id    uuid        not null references users (id) on delete cascade,
    token_hash varchar(64) not null,
    expires_at timestamptz not null,
    used_at    timestamptz,
    created_at timestamptz not null default now(),
    constraint uq_password_reset_tokens_hash unique (token_hash)
);

create index ix_password_reset_tokens_user on password_reset_tokens (user_id);
