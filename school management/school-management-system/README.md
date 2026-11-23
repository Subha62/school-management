# School Management System

A secure, maintainable school-management platform built as a **modular monolith** (Spring Boot backend +
React frontend in one repository). Initial roles: `ROLE_ADMIN`, `ROLE_TEACHER`, `ROLE_STUDENT`.

> **Status: all nine phases are complete.** Authentication, role-based access, the
> academic structure, student/teacher/guardian records, enrollment, promotion, user management, the weekly
> timetable, attendance with reports, assignments with file uploads and review, examinations with grade scale,
> marks, locking, publication and report cards, announcements with read tracking, offline fees with receipts and
> reversals, a persistent append-only audit trail, role dashboards, OpenAPI documentation, CI and a production
> readiness review are implemented. See [Roadmap](#roadmap) and the limitations listed per module.

## Architecture

```text
school-management-system/
├── backend/    Spring Boot 3.5 (Java 17), Maven wrapper, Flyway, PostgreSQL
├── frontend/   React 19 + TypeScript + Vite + Material UI
├── docs/       Architecture, database, security, API and deployment notes
├── docker-compose.yml   PostgreSQL + Mailpit for local development
└── .github/workflows/   CI
```

Details and diagrams: [docs/architecture.md](docs/architecture.md). Using the application: [docs/user-guide.md](docs/user-guide.md).

## Technology stack

| Area     | Technology                                                                                    |
| -------- | --------------------------------------------------------------------------------------------- |
| Backend  | Java 17, Spring Boot 3.5, Spring Security 6, Spring Data JPA, Flyway, PostgreSQL, springdoc   |
| Frontend | React, TypeScript (strict), Vite, React Router, TanStack Query, Axios, React Hook Form, Zod, MUI |
| Testing  | JUnit 5, Mockito, Spring Security Test, Testcontainers (PostgreSQL); Vitest, Testing Library, Playwright |
| Tooling  | Maven Wrapper, npm, ESLint, Prettier, Docker Compose, GitHub Actions                          |

## Prerequisites

- JDK 17
- Node.js 20.9 or newer (22 LTS recommended; CI uses 22)
- Docker Desktop / Docker Engine (PostgreSQL, Mailpit and the Testcontainers integration tests)

Maven does not need to be installed; use the wrapper (`backend/mvnw`, `backend/mvnw.cmd`).

## Local setup

1. Copy the environment template (optional for the defaults below):

   ```powershell
   Copy-Item .env.example .env
   ```

2. Start PostgreSQL and Mailpit:

   ```powershell
   docker compose up -d
   docker compose ps          # both services should become "healthy"
   ```

3. Start the backend (profile `dev` is the default):

   ```powershell
   cd backend
   .\mvnw.cmd spring-boot:run
   ```

4. Start the frontend in a second terminal:

   ```powershell
   cd frontend
   npm install
   npm run dev
   ```

5. Open <http://localhost:5173>. The Vite dev server proxies `/api` to `http://localhost:8080`, keeping
   the browser on one origin so cookies and CSRF behave as in production.

### Running without Docker

Docker is optional. With any PostgreSQL 15+ you can run everything locally:

```powershell
# 1. A database (example; use your own cluster if you have one)
initdb -D $HOME\sms-local\pg -U school_app --pwfile=pw.txt -A scram-sha-256 -E UTF8
postgres -D $HOME\sms-local\pg -p 5544            # leave running; then: createdb school_management

# 2. Backend jar (dev profile seeds the demo school the first time, on a local database only)
cd backend; .\mvnw.cmd -DskipTests package
$env:DB_URL = 'jdbc:postgresql://localhost:5544/school_management'; $env:DB_PASSWORD = '<password>'
java -jar target\school-management-backend-0.0.1-SNAPSHOT.jar --server.port=8085

# 3. Frontend, pointing the dev proxy at that backend
cd frontend; npm install
$env:VITE_DEV_API_TARGET = 'http://localhost:8085'; npm run dev
```

Mail (password reset) needs an SMTP server such as Mailpit; without one, everything else still works.

### Demo accounts (development only)

All demo accounts share the password `Demo@Pass2026!` (override with `DEMO_PASSWORD`). They exist only with the `dev`
profile on a local database.

| Role | Sign in with | Name |
| ---- | ------------ | ---- |
| Administrator | `admin@demo.school` | Asha Admin |
| Teacher | `teacher@demo.school` or `T-0001` | Tara Teacher |
| Student | `student@demo.school` or `S-0001` | Sam Student |

### Useful URLs

| What                       | URL                                     |
| -------------------------- | --------------------------------------- |
| Frontend                   | <http://localhost:5173>                 |
| Backend health             | <http://localhost:8080/actuator/health> |
| Public status endpoint     | <http://localhost:8080/api/v1/system/info> |
| Swagger UI (dev only)      | <http://localhost:8080/swagger-ui.html> |
| OpenAPI JSON (dev, test)   | <http://localhost:8080/v3/api-docs>     |
| Mailpit inbox              | <http://localhost:8025>                 |

## Environment variables

See [.env.example](.env.example) (fake values only). The backend reads them as environment variables
(`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `CORS_ALLOWED_ORIGINS`, `JWT_SIGNING_SECRET`, `COOKIE_SECURE`,
`COOKIE_SAME_SITE`, `FRONTEND_BASE_URL`, `MAIL_*`, `FILE_STORAGE_PROVIDER`, `FILE_STORAGE_PATH`, `SERVER_PORT`, `DB_POOL_SIZE`,
`INITIAL_ADMIN_*`). The `dev` profile has local-only fallbacks that match `docker-compose.yml` (including a
throwaway JWT secret); the `prod` profile has **no** fallbacks and fails fast when a value is missing, and the
JWT secret must be at least 32 bytes. `.env` is not loaded automatically by Spring: export the variables in
your shell or IDE run configuration, or let Docker Compose read it for the database containers.

## Test commands

```powershell
# Backend: compile, unit tests and Testcontainers integration tests (Docker required for the latter)
cd backend
.\mvnw.cmd verify

# Frontend
cd frontend
npm run typecheck
npm run lint
npm run format:check
npm test
npm run build
npm run e2e        # Playwright, 6 viewports from 320x568 to 1440x900 (run once: npx playwright install chromium)
```

Integration tests that need PostgreSQL use Testcontainers (a shared container is started once). They are
**skipped automatically** when Docker is unavailable; CI runs them. Without Docker you can instead point them at
a **throwaway** PostgreSQL database (the tests truncate tables) by setting `IT_DB_URL`, `IT_DB_USERNAME` and
`IT_DB_PASSWORD`:

```powershell
$env:IT_DB_URL = 'jdbc:postgresql://localhost:5433/school_it'
$env:IT_DB_USERNAME = 'school_app'; $env:IT_DB_PASSWORD = '<password>'
.\mvnw.cmd verify
```

H2 is intentionally not used.

## Authentication

Sign in at <http://localhost:5173/login> with an **email** or a **login ID** (a student's admission number or a
teacher's employee ID, which become the account's username when an administrator creates the student or teacher).
Sessions use two `HttpOnly` cookies: a
15-minute access token and a 7-day rotating refresh token (only its SHA-256 hash is stored). Tokens are never
placed in response bodies, `localStorage` or `sessionStorage`. Details: [docs/security.md](docs/security.md).

| Endpoint | Purpose |
| -------- | ------- |
| `POST /api/v1/auth/login` | Sign in, set cookies |
| `POST /api/v1/auth/refresh` | Rotate the refresh token, issue a new access token |
| `POST /api/v1/auth/logout` | Revoke the refresh token family, clear cookies |
| `GET /api/v1/auth/me` | Identity and roles of the current user |
| `POST /api/v1/auth/change-password` | Change password; ends all other sessions |
| `POST /api/v1/auth/forgot-password` | Email a reset link (always answers `202`) |
| `POST /api/v1/auth/reset-password` | Set a new password with the emailed token |

In development the reset email is delivered to Mailpit (<http://localhost:8025>).

## Demo users (development only)

With the `dev` profile and a **local** database, the backend seeds a demo school on first start. The accounts
below all use the password `Demo@Pass2026!` (override with `DEMO_PASSWORD`). They are created only when the
datasource points at `localhost`, so a misconfigured deployment can never receive them. **Never use these
credentials outside local development.**

| Role | Email | Login ID |
| ---- | ----- | -------- |
| Admin | `admin@demo.school` | `admin` |
| Teacher | `teacher@demo.school` | `T-0001` |
| Student | `student@demo.school` | `S-0001` |

The seed also creates the demo academic data: an **active academic year** for the current period, **Grade 8**,
section **8-A** (class teacher: the demo teacher) and the subjects Mathematics, English, Science, Social Studies
and Computer Science, each assigned to 8-A and taught by the demo teacher. Phase 4 adds profiles for the demo
teacher and student, a demo guardian linked to the student, and the student's enrollment in 8-A (roll number 1).
Existing development databases from earlier phases receive the new data on the next start.

## People and enrollment (administrators)

Open **Users**, **Students**, **Teachers**, **Guardians**, **Enrollments** and **Student promotion**:

- **Students and teachers** are created together with their sign-in account. The admission number / employee ID
  is the username and **cannot be changed later**. The administrator sets the initial password (the form can
  generate a strong one) and passes it on; there is no forced change at first sign-in yet.
- **Student status**: withdrawing or graduating a student ends their active enrollment (kept as history).
  Only active students can be enrolled. Inactive, resigned or retired teachers cannot be assigned to sections.
- **Guardians** are separate records linked to students; a student has at most one *primary* guardian (the first
  link is primary automatically, and removing the primary promotes the next). A linked guardian cannot be deleted.
- **Enrollment**: one active enrollment per student and academic year. Capacity is enforced under a database
  lock, so two requests cannot take the last seat. Roll numbers are unique among a section's active students and
  assigned automatically when left empty. A **transfer** stays within the year; old rows are never rewritten.
  A section that has had students cannot be deleted, and its capacity cannot drop below its active students.
- **Promotion** moves selected students to sections of a later, open academic year in one run. Each student is
  checked on their own (active student, active source enrollment, no enrollment in the target year, active
  target section with a free seat); students who cannot move are reported with the reason and left untouched.
  Every run is recorded (who, when, counts) and listed in the promotion history.
- **Users**: suspend, archive or reactivate any account (suspending signs the person out everywhere), clear a
  sign-in lockout and create further administrators. You cannot change your own status, and the last active
  administrator cannot be disabled.
- Profile photos are not part of this phase (file storage is used by assignments only so far).

API details: [docs/api-conventions.md](docs/api-conventions.md); tables: [docs/database-model.md](docs/database-model.md).

## Timetable and attendance

Administrators use **Timetable** and **Attendance reports**; teachers use **My timetable** and **Attendance**;
students use **My timetable** and **Attendance**.

- **Timetable slots** belong to a class and a subject that is assigned to it (Teacher assignments), with a
  teacher (default: the assigned one), weekday, period, start and end time, optional room and effective dates
  (default: the whole academic year). Two slots **clash** when they share a weekday, overlap in time and in
  dates, and share the class, the teacher or the room (rooms compare case-insensitively). Slots that only touch
  (09:00-09:45 and 09:45-10:30) do not clash, and inactive slots are ignored. Clashes answer `409` with a message
  naming what is in the way. The timetable of a closed academic year is read-only. Changing an assignment's
  teacher later does not change existing slots; edit them if the teacher changes.
- The weekly view is a column per day on large screens and a day selector with an agenda below 900px. Teachers
  and students only ever see their own week (a student sees the class they are enrolled in).
- **Taking attendance**: the **class teacher** takes the *daily* register of their class; a **subject teacher**
  takes the register of their own subject (optionally for a period). Opening a register that already exists
  returns it. Only students currently enrolled in the class can be marked, and each student has one mark per
  register. A teacher can open registers for today and the previous 7 days
  (`ATTENDANCE_TEACHER_BACKDATE_DAYS`); never for the future or outside the academic year.
- **Finalizing** needs every enrolled student marked. A finalized register is closed to teachers. An
  **administrator** can still correct any mark under *Attendance reports > Sessions and corrections*: a reason
  is required, the old and new status are kept in a permanent history, and an audit event is recorded.
- **Percentages** count finalized registers only. Present and late count as attended, a half day as half,
  absent as not attended, and excused days are left out of both sides. A student with nothing counted has no
  percentage (not 0%). The *below threshold* report (default 75%, `ATTENDANCE_THRESHOLD_PERCENT`) lists students
  under the line for a chosen period, class and attendance type, lowest first (at most 500 rows).
- Demo data adds a Monday-Friday timetable for 8-A and ten school days of finalized attendance for the demo
  student (three absences, so they appear in the below-threshold report).
- Limitations: attendance is checked against the students enrolled *now* (enrollments keep no end date, so a
  student who has left the class is not offered for past dates); teachers' substitutions and leave are not
  modelled; there is no email or SMS notification of absences yet.

## Assignments

Teachers use **Assignments** and **Submission review**; students use **Assignments** and **Submissions**.
There is no administrator screen for assignments.

- **Setting work**: a teacher creates an assignment only for a class and subject they are assigned to in the
  **active** academic year (otherwise `403`). The due date must be in the future, maximum marks are 0.01-1000
  (two decimals). Lifecycle: **Draft** (invisible to students) > **Published** > **Closed** > **Archived**.
  Closed work can be reopened (needs a future due date or late work allowed), only drafts can be deleted, and
  maximum marks cannot change once someone has handed in. Another teacher's assignment answers `404`.
- **Files**: up to 10 files per assignment and 5 per submission, 10 MB each. Allowed types: pdf, doc, docx, xls,
  xlsx, ppt, pptx, txt, png, jpg, jpeg. Type is checked by extension **and** file signature, names are
  sanitised, and stored under random keys, never the uploaded name. Downloads are forced attachments with
  `nosniff`, go through the API (so access rules apply), and are limited to the assignment's teacher and
  class. A failed request removes any files it had already stored. **There is no virus scanning**; add a
  scanner in front of the storage if files come from untrusted users.
- **Handing in**: a student sees only published or closed assignments of the class they are enrolled in. A
  submission needs text and/or a file, and can be changed until it is reviewed. After the due date the work is
  marked **Late** if the teacher allows late work, otherwise it is refused (`422`). When the teacher asks for a
  resubmission the student can answer again even after the due date, and it is not marked late.
- **Reviewing**: *Reviewed* needs marks from 0 up to the maximum (two decimals); *Resubmission requested* needs
  feedback. Students see marks only once reviewed, and feedback when reviewed or a resubmission is requested. A
  stale edit answers `409`. The review queue shows work waiting for review first, oldest first.
- Sections and teacher assignments that have assignments cannot be deleted (`409`).
- Demo data adds a published "Worksheet 1" and a closed "Reading log" with a reviewed submission (8.5/10).
- Limitations: no virus scanning, no per-student extensions, no group work or rubrics, no email notification,
  and no administrator view of assignments.

## Examinations

Administrators use **Exams**, **Grade rules** and **Results**; teachers use **Exams and marks**; students use
**Exams and results** and **Report cards**.

- **Grade scale**: inclusive percentage bands with two decimals (for example A+ 90-100, A 75-89.99). Bands cannot
  overlap, and an exam cannot be locked or published until the bands cover 0 to 100 with no gaps.
- **Exams** belong to a grade and the active academic year and list their subjects with a maximum and pass mark. The
  lifecycle is **Draft > Open for marks > Locked > Published**; only administrators move an exam, a locked exam can
  be sent back to marks entry, and publication is final.
- **Marks entry**: a teacher enters marks only for a subject and class they are assigned to, only while the exam is
  open, for students enrolled in that class. Marks run from 0 to the subject maximum with two decimals; an absent
  student has no marks; clearing an entry removes it. The class teacher can also write an overall comment per
  student.
- **Locking** needs every enrolled student marked (or absent) in every subject their class takes, so "not entered"
  can never be read as zero. Locked marks can no longer change, even by a request that was already in flight.
- **Results**: total, percentage (rounded half up to two decimals), grade, and pass or fail. An absent subject scores
  zero, keeps its maximum in the total and counts as failed; a student passes when every subject reaches its pass
  mark. Administrators see a preview while the exam is locked; **publishing freezes the results**, so later changes to
  the grade scale cannot alter a report card, and only then can students see them.
- **Report cards** are printable pages (use the Print button; the menus and buttons are hidden on paper).
- Demo data adds a grade scale, a published "Term 1" exam with results for the demo class and an open "Mid-term"
  exam with marks still to enter.
- Limitations: published results cannot be corrected, there are no class ranks, and administrators cannot enter
  marks on a teacher's behalf (reassign the subject or reopen the exam instead).

## Announcements

Administrators use **Announcements**; teachers use **Announcements** (to write to their own classes and to read);
students read them under **Announcements**. A notice has an audience (everyone, administrators, teachers, students
or one class), optional start and expiry times, and is plain text.

- It is visible only when it is published, has started, has not expired and is meant for the reader: students and
  teachers of a class see class announcements, a subject teacher or the class teacher can write to their class, and
  everything else is hidden by the server (`404`), not just in the screen.
- Drafts can change audience; a published announcement keeps its audience, and is archived instead of deleted.
- Each person's reads are recorded (marking twice is harmless); readers see an unread badge and administrators see
  how many people have read each announcement.
- Demo data adds announcements for each audience and a draft.

## Fees (offline)

Administrators use **Fee categories**, **Fee structures**, **Student fees** and **Payments**; students see their own
fees and receipts under **Fees**. **Teachers cannot see any financial data.**

- A **fee structure** is what a grade pays for a category in the active year and when it falls due. **Generate** bills
  every enrolled student of the grade (run it again later to bill students who joined since). Bills are copies, so
  changing a structure never rewrites them; once fees exist, a structure's amount and due date are frozen.
- **Statuses**: not due, due (within 14 days, `FEE_DUE_SOON_DAYS`), partially paid, paid, overdue (past the due date
  with a balance) and waived. They are worked out from the amounts and today's date in the school's time zone.
- **Payments** are positive, cannot exceed the balance and get a unique receipt such as `REC-2026-000042`. Two
  cashiers paying the same fee at once cannot overpay it. Payments are never edited or deleted: an administrator
  **reverses** a mistaken one with a reason, which keeps both rows and restores the balance. A fee with no payments
  can be waived with a reason.
- **Reports**: billed, collected, outstanding and overdue totals per year, grade and category, and money received in
  a period by payment method, net of reversals. Receipts can be printed by administrators and by the student.
- Demo data adds Tuition and Transport fees for the demo class, one partial UPI payment and one overdue bill.
- Limitations: no online payment gateway, no partial reversal or refunds, no discounts other than a full waiver, no
  reminders by email or SMS, and fees are billed to students only (guardians have no sign-in yet).

## Dashboards, audit logs and operations

- **Dashboards**: every role's home page shows live numbers for that role: administrators see people and classes,
  today's registers, the fee snapshot, exams in progress, live announcements and recent activity; teachers see
  today's periods, submissions waiting for review, marks still to enter, registers to take and unread
  announcements; students see their class, today's periods, attendance this month, upcoming work, their latest
  result, fees and unread announcements. If the numbers cannot be loaded, the page says so and keeps the backend
  status card.
- **Audit logs** (administrators, *System > Audit logs*): the permanent record of who did what and when, with filters
  by action, entity, person, dates and text, and a Before / After view of changed values. Passwords, tokens and
  similar values are never stored. The trail is append-only: it cannot be edited or deleted from the application.
- **API documentation**: Swagger UI and the OpenAPI document (development and test only) describe every endpoint,
  its role and how authentication works.
- **Production**: see the [production readiness checklist](docs/deployment.md#production-readiness-checklist).
- **CI** (`.github/workflows/ci.yml`): backend build and tests on Java 17 (including the endpoint security sweep),
  then frontend install, dependency audit, type check, lint, formatting, unit tests, production build and the
  responsive Playwright suite; test reports are kept when a run fails. No production secrets are used.

## Academic configuration (administrators)

Sign in as the admin and open the **Academic years**, **Grades**, **Sections**, **Subjects** and **Teacher
assignments** pages. Highlights:

- Only **one academic year can be active**; activating never closes another year, and **closing is
  irreversible** (the year's sections and assignments become read-only).
- Grades are ordered by *display order*; subject codes are unique per school (stored upper case); section names
  are unique per grade and year.
- A section's year and grade cannot change after creation. Sections and subject assignments can name a **class
  teacher / subject teacher** (searched among active teacher accounts).
- Items that are in use cannot be deleted; mark grades and subjects *inactive* instead.
- Every change is audited, edits use optimistic locking (a stale edit gets a conflict message), and lists are
  paged, searchable and sortable. API details: [docs/api-conventions.md](docs/api-conventions.md).

### First administrator in a new deployment

Set `INITIAL_ADMIN_EMAIL` and `INITIAL_ADMIN_PASSWORD` (policy-compliant) before the first start. The admin and
school are created only if no active administrator exists; remove the variables afterwards.

## Production build

```powershell
cd backend;  .\mvnw.cmd -DskipTests package      # backend/target/school-management-backend-*.jar
cd frontend; npm ci; npm run build               # frontend/dist (static files)
```

See [docs/deployment.md](docs/deployment.md).

## Security notes

Deny-by-default stateless security, HttpOnly cookie sessions with rotating refresh tokens, CSRF cookie
(`XSRF-TOKEN` / `X-XSRF-TOKEN`), account lockout and login rate limiting, generic login errors, BCrypt,
explicit CORS origins, security headers, RFC 7807 errors without internal details. Full list and known
limitations: [docs/security.md](docs/security.md). Never commit `.env`, keys or credentials.

## Troubleshooting

| Symptom | Fix |
| ------- | --- |
| `password authentication failed for user "school_app"` at backend start | Another PostgreSQL is already listening on port 5432. Set `POSTGRES_PORT=5433` for Compose and `DB_URL=jdbc:postgresql://localhost:5433/school_management`, or stop the other instance. |
| Backend cannot connect to the database | Run `docker compose ps`; wait for `healthy`. |
| Backend fails with `jwt.secret must be at least 32 bytes long` | Outside the `dev` profile `JWT_SIGNING_SECRET` is required (32+ bytes). |
| Cannot sign in over plain HTTP outside `dev` | Cookies are `Secure` by default; use HTTPS or set `COOKIE_SECURE=false` for a local test only. |
| Sign-in answers "temporarily unavailable" or `429` | The account is locked for 15 minutes after 5 failed attempts, or the rate limiter tripped. Wait, or restart the backend in development. |
| Demo accounts are missing | Seeding runs only with the `dev` profile and a `localhost` database, and only once. |
| Reset email does not arrive | Start Mailpit (`docker compose up -d`) and open <http://localhost:8025>. |
| Frontend shows "Backend unavailable" | Start the backend, or set `VITE_DEV_API_TARGET` to its URL. |
| Integration tests are skipped | Start Docker, or set `IT_DB_URL` for a throwaway database. |
| `EBADENGINE` warnings on npm install | Use Node 20.9+ (22 LTS recommended). |

## Roadmap

1. **Foundation**: done
2. **Authentication and role-based access**: done
3. **Academic configuration**: done
4. **Students, guardians, teachers, enrollment, promotion**: done
5. **Timetable and attendance**: done
6. **Assignments**: done
7. **Examinations and report cards**: done
8. **Announcements and fees**: done
9. **Audit logs, dashboards, quality and delivery**: done
