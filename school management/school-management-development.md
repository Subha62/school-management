# School Management System Development Instructions

## 1. Project Mission

Build a production-quality, fully responsive school-management web application as a modular monolith.

**Project name:** `school-management-system`

### Initial roles

- `ROLE_ADMIN`
- `ROLE_TEACHER`
- `ROLE_STUDENT`

### First-release modules

- Authentication and role-based access
- School and academic configuration
- Student, guardian, and teacher management
- Enrollment and student promotion
- Attendance
- Timetables
- Assignments and submissions
- Examinations, marks, grades, and report cards
- Announcements
- Basic offline fee management
- Audit logs and dashboards

Do not create placeholder-only screens. Each feature must be implemented as a working vertical slice with database migrations, persistence, business rules, APIs, authorization, frontend integration, validation, error handling, and meaningful tests.

---

## 2. Technology Stack

### Backend

- Java 17
- Spring Boot 3.x
- Maven and Maven Wrapper
- Spring Web
- Spring Security 6
- Spring Data JPA
- Jakarta Bean Validation
- PostgreSQL
- Flyway
- Spring Boot Actuator
- springdoc-openapi
- MapStruct only where mapping complexity justifies it
- Lombok only when configured correctly
- JUnit 5, Mockito, Spring Security Test
- Testcontainers with PostgreSQL

### Frontend

- React with TypeScript and Vite
- React Router
- TanStack Query
- Axios
- React Hook Form
- Zod
- Material UI
- Vitest and React Testing Library
- Playwright for responsive end-to-end tests
- ESLint and Prettier

### Development and delivery

- Docker Compose for PostgreSQL and Mailpit
- Git and GitHub
- GitHub Actions

---

## 3. Repository Structure

```text
school-management-system/
├── backend/
├── frontend/
├── docs/
├── docker-compose.yml
├── .env.example
├── .gitignore
├── README.md
└── .github/
    ├── copilot-instructions.md
    └── workflows/
```

Backend base package: `com.schoolmanagement`

```text
com.schoolmanagement
├── auth
├── user
├── student
├── guardian
├── teacher
├── academic
├── attendance
├── timetable
├── assignment
├── examination
├── announcement
├── fee
├── audit
├── file
├── notification
├── security
├── config
└── common
```

Use practical package names such as `controller`, `service`, `repository`, `entity`, `dto`, `mapper`, `validation`, and `exception`. Do not create unnecessary interfaces or abstractions.

Frontend structure:

```text
frontend/src/
├── api/
├── app/
├── auth/
├── components/
├── layouts/
├── features/
├── hooks/
├── pages/
│   ├── admin/
│   ├── teacher/
│   └── student/
├── routes/
├── schemas/
├── types/
├── utils/
├── main.tsx
└── App.tsx
```

---

## 4. Engineering Rules

- Use Java 17 and Spring Boot 3 Jakarta packages. Never use legacy `javax` APIs.
- Prefer UUID primary keys unless a numeric sequence is clearly better.
- Use `Instant` for audit timestamps, `LocalDate` for dates, and `LocalTime` for timetable times.
- Store timestamps in UTC and display them using the school's configured timezone.
- Use `BigDecimal` for all financial values. Never use `double` or `float` for money.
- Use optimistic locking with `@Version` when concurrent edits could lose data.
- Do not expose JPA entities through controllers. Use request and response DTOs.
- Validate every external input.
- Keep transaction boundaries in the service layer.
- Avoid N+1 database queries.
- Paginate collection endpoints and reject unreasonable page sizes.
- Use enums for controlled statuses.
- Return RFC 7807 Problem Details or a consistent equivalent.
- Never expose stack traces, SQL statements, secrets, tokens, or sensitive student data.
- Keep business logic out of controllers and React components.
- Use strict TypeScript. Avoid `any` unless documented and unavoidable.
- Never commit credentials, build output, uploaded files, IDE caches, or local environment files.

---

## 5. Security and Authentication

Use Spring Security 6 and BCrypt password hashing.

### Authentication model

- Short-lived access tokens
- Rotating refresh tokens
- Access and refresh tokens delivered through `HttpOnly` cookies
- `Secure` cookies in production
- Appropriate `SameSite=Lax` or `SameSite=Strict`
- CSRF protection compatible with cookie authentication
- Never store tokens in `localStorage` or `sessionStorage`
- Store only hashed refresh tokens in PostgreSQL
- Revoke the previous refresh token after rotation
- Revoke the active refresh token during logout

### Required endpoints

```text
POST /api/v1/auth/login
POST /api/v1/auth/refresh
POST /api/v1/auth/logout
GET  /api/v1/auth/me
POST /api/v1/auth/change-password
POST /api/v1/auth/forgot-password
POST /api/v1/auth/reset-password
```

Add account status checks, temporary lockout after repeated failures, login rate limiting, generic authentication error messages, password rules, CORS from environment variables, security headers, sanitized audit events, and development password-reset delivery through Mailpit.

Never log passwords, access tokens, refresh tokens, reset tokens, or complete sensitive student records.

Frontend route guards improve user experience, but backend authorization is always the final authority.

---

## 6. Core Domain Model

### School and users

Create `School`, `User`, `Role`, and `UserRole` records. Major data must include `schoolId` to allow future multi-school support, but do not implement tenant routing in the first release.

`AccountStatus`: `PENDING`, `ACTIVE`, `LOCKED`, `SUSPENDED`, `ARCHIVED`.

Do not allow teachers or students to change roles. Never suspend or remove the final active administrator.

### Academic structure

Create:

- `AcademicYear`: `PLANNED`, `ACTIVE`, `CLOSED`
- `GradeLevel`
- `Section`
- `Subject`
- `SectionSubject`

Only one academic year may be active for a school. Prevent duplicate section names within the same grade and academic year, and duplicate subject codes within a school.

### Students and guardians

Create:

- `StudentProfile` with a unique admission number per school
- `Guardian`
- `StudentGuardian`
- `StudentEnrollment`

Enrollment statuses: `ACTIVE`, `TRANSFERRED`, `PROMOTED`, `WITHDRAWN`, `COMPLETED`.

Promotion must create a new enrollment in the target academic year and preserve historical enrollment. Support individual and bulk promotion, validate source and target years, record the actor, and return a summary.

### Teachers

Create `TeacherProfile` with a unique employee ID per school. Teachers may access only their profile, assigned sections and subjects, eligible students, authorized attendance, owned assignments, and authorized marks.

### Attendance

Support daily and subject-wise attendance.

Statuses: `PRESENT`, `ABSENT`, `LATE`, `HALF_DAY`, `EXCUSED`.

Create `AttendanceSession` and `AttendanceRecord`. Permit one attendance record per student and session. Only enrolled students may be marked. Teachers may not change finalized attendance. Admin corrections require a reason and audit event. Generate monthly percentages and a below-threshold report, defaulting to 75%.

### Timetable

Create `TimetableEntry` with academic year, section, subject, teacher, room, day, period, start/end time, effective dates, and status. Prevent teacher, section, room, and overlapping-time conflicts. Provide weekly section, teacher, and student views.

### Assignments

Create `Assignment`, `AssignmentAttachment`, `AssignmentSubmission`, and `SubmissionAttachment`.

Statuses:

- Assignment: `DRAFT`, `PUBLISHED`, `CLOSED`, `ARCHIVED`
- Submission: `SUBMITTED`, `LATE`, `REVIEWED`, `RESUBMISSION_REQUESTED`

Validate authorization, due dates, maximum marks, file types, and file sizes. Use a storage abstraction, safe server-generated names, and local development storage. Prevent path traversal. Keep the design ready for Oracle Cloud Object Storage or an S3-compatible provider.

### Examinations

Create `Exam`, `ExamSubject`, `StudentMark`, and `GradeRule`.

Exam statuses: `DRAFT`, `OPEN_FOR_MARKS`, `LOCKED`, `PUBLISHED`.

Validate mark ranges and absent-student rules. Grade boundaries must not overlap. Teachers may enter marks only for assigned subjects and sections. Only admins may lock or publish. Students may view only published results. Provide subject results, totals, percentages, grades, pass/fail status, comments, and printable HTML report cards.

### Announcements

Create `Announcement` and `AnnouncementRead`.

Audience types: `ALL`, `ADMINS`, `TEACHERS`, `STUDENTS`, `SECTION`.

Statuses: `DRAFT`, `PUBLISHED`, `ARCHIVED`. Enforce audience eligibility, publish dates, and expiry dates.

### Fees

Implement offline fees first with `FeeCategory`, `FeeStructure`, `StudentFee`, `Payment`, and auditable payment reversals.

Fee statuses: `NOT_DUE`, `DUE`, `PARTIALLY_PAID`, `PAID`, `OVERDUE`, `WAIVED`.

Require positive payments, prevent overpayment, generate unique receipts, preserve financial history, and prevent teachers from viewing financial data unless explicitly authorized.

### Audit logs

Audit login outcomes, password changes, account and role changes, promotions, attendance corrections, mark changes, exam lock/publication, payments/reversals, and important configuration changes. Sanitize old/new JSON values and never include credentials or tokens.

---

## 7. API Conventions

Prefix endpoints with `/api/v1`.

Use correct HTTP methods and status codes, pagination, sorting, filters, validation, OpenAPI documentation, and consistent Problem Details. Never expose internal class names or SQL.

Default page size: 20. Set a safe maximum page size, such as 100.

---

## 8. Frontend Requirements

Create separate Admin, Teacher, and Student application layouts.

### Common pages

- Login
- Forgot and reset password
- Change password
- Profile
- Unauthorized
- Not found

### Admin pages

Dashboard, users, students, teachers, guardians, academic years, grades, sections, subjects, teacher assignments, enrollments, promotion, timetable, attendance reports, exams, grade rules, results, announcements, fee categories, fee structures, student fees, payments, and audit logs.

### Teacher pages

Dashboard, timetable, classes, students, attendance, assignments, submission review, marks, announcements, and profile.

### Student pages

Dashboard, timetable, attendance, assignments, submissions, results, report cards, announcements, fees, and profile.

Use TanStack Query for server state, React Hook Form and Zod for forms, centralized Axios with `withCredentials: true`, and one controlled refresh attempt without infinite loops. Provide loading, empty, success, validation, and error states. Do not display raw backend errors.

---

## 9. Full Responsive Design

The application must work at:

- 320px small mobile
- 375px standard mobile
- 425px large mobile
- 768px tablet
- 1024px laptop
- 1440px desktop
- 1536px and larger screens

Use Material UI breakpoints consistently.

### Layout rules

- Temporary Drawer and accessible menu button on mobile
- Persistent Drawer on desktop
- Drawer closes after mobile navigation
- No page-level horizontal overflow at 320px
- Responsive `Grid`, `Stack`, `Box`, and `Container`
- One-column forms below the `sm` breakpoint
- Dashboard cards: one per row on mobile, two on tablet, and three or four on desktop
- Important touch targets at least 44 by 44 pixels
- Visible keyboard focus and accessible labels
- Do not depend on hover interactions
- Long text must wrap or truncate safely
- Dialogs should become full-screen or nearly full-width on small screens

### Responsive data displays

Do not compress large tables into unreadable mobile layouts. Use cards or compact lists on mobile, hide only nonessential columns, preserve primary actions, and use an action menu for secondary actions. If table scrolling is necessary, limit scrolling to the table container.

Use a weekly timetable grid on desktop and a day selector, tabs, or agenda list on mobile. Report cards and fee receipts require A4 print styles that hide navigation and action controls.

### Responsive testing

Configure Playwright projects for:

- 320 x 568
- 375 x 667
- 390 x 844
- 768 x 1024
- 1024 x 768
- 1440 x 900

Test login, dashboards, students, teachers, attendance, timetable, assignments, marks, report cards, announcements, fees, and receipts. Verify no page overflow, usable navigation, accessible actions, fitting dialogs, visible validation, and responsive loading/error states.

---

## 10. Database and Configuration

Flyway owns schema evolution under `backend/src/main/resources/db/migration`.

Suggested migrations:

```text
V1__create_school_and_users.sql
V2__create_academic_structure.sql
V3__create_students_and_guardians.sql
V4__create_teachers.sql
V5__create_attendance.sql
V6__create_timetable.sql
V7__create_assignments.sql
V8__create_examinations.sql
V9__create_announcements.sql
V10__create_fees.sql
V11__create_audit_logs.sql
```

Use foreign keys, unique constraints, check constraints, and useful indexes. Never modify a released migration. Add a corrective migration. Configure Hibernate `ddl-auto=validate` outside tests.

Create:

- `application.yml`
- `application-dev.yml`
- `application-test.yml`
- `application-prod.yml`
- `.env.example` with fake values only

Use environment variables for database access, signing keys, cookie settings, CORS origins, file storage, mail, and optional initial-admin settings. Never put real secrets in source control.

Docker Compose must initially provide PostgreSQL and Mailpit with health checks and a persistent PostgreSQL volume.

---

## 11. Testing and CI

Test meaningful behavior, not mock implementation details.

### Backend coverage priorities

- Authentication and token rotation
- Role and ownership authorization
- Enrollment and promotion
- Attendance finalization/correction
- Timetable conflict detection
- Assignment submission/review
- Marks and grading
- Exam locking/publication
- Fee calculations and reversals

Use PostgreSQL Testcontainers for integration tests, not H2.

### Frontend coverage priorities

- Login and protected routes
- Role navigation
- Form validation
- Attendance
- Assignment submission
- Marks entry
- API error presentation
- Responsive navigation and layouts

GitHub Actions must use Java 17 and run backend build/tests, frontend `npm ci`, type checking, ESLint, tests, and production build without production secrets.

---

## 12. Documentation

Maintain:

- `README.md`
- `docs/architecture.md`
- `docs/database-model.md`
- `docs/security.md`
- `docs/api-conventions.md`
- `docs/deployment.md`

Include local setup, environment variables, Docker usage, start/test/build commands, development demo users, Swagger URL, security notes, deployment considerations, troubleshooting, and Mermaid diagrams for architecture, authentication flow, and core relationships.

Development-only seed data may include one demo school, admin, teacher, student, active year, grade, section, and subjects. Never hard-code production passwords.

---

## 13. Implementation Phases

1. **Foundation:** repository, backend, frontend, Docker Compose, profiles, Flyway, global errors, testing, linting, README.
2. **Authentication:** school, users, roles, cookies, token rotation, logout, current user, password flows, protected routes.
3. **Academics:** years, grades, sections, subjects, and teacher assignments.
4. **People:** students, guardians, teachers, enrollment, and promotion.
5. **Operations:** timetable, attendance, finalization, corrections, and reports.
6. **Assignments:** publishing, attachments, submissions, review, feedback, and marks.
7. **Examinations:** exams, marks, grades, locking, publication, results, and report cards.
8. **Communication and fees:** announcements, reads, fee configuration, payments, receipts, and reports.
9. **Quality and delivery:** audit logs, dashboards, testing gaps, OpenAPI, CI, documentation, and production review.

Complete one phase at a time. Do not start the next phase without explicit approval.

---

## 14. Execution Rules for GitHub Copilot Agent

1. Inspect the repository before modifying it.
2. Before each phase, report the current state, architecture decisions, target files, and planned commands.
3. Preserve working code unless a change is necessary.
4. Do not replace real implementations with placeholders.
5. Do not silently remove requirements.
6. Never weaken security, validation, migrations, TypeScript strictness, or tests to hide a failure.
7. Never run destructive database, Git, or filesystem commands without clearly explaining the impact.
8. After each phase, run backend compilation/tests and frontend type-checking, linting, tests, and production build.
9. Fix failures before reporting completion.
10. Stop after the current approved phase.

A feature is complete only when the migration, persistence, business rules, API, validation, authorization, frontend integration, loading/error states, critical tests, build, and documentation are complete.

---

## 15. Current Task: Phase 1 Only

Inspect the repository and implement only Phase 1.

Phase 1 must include:

- Root monorepo structure
- Java 17 Spring Boot 3 Maven backend and Maven Wrapper
- React TypeScript Vite frontend
- Required base dependencies
- PostgreSQL and Mailpit Docker Compose services
- Application profiles
- Flyway foundation
- RFC 7807-compatible global error handling
- ESLint, Prettier, Vitest, React Testing Library, and Playwright setup
- Responsive Material UI application shell
- `.gitignore` and fake `.env.example`
- Initial README and documentation skeletons
- GitHub Actions validation workflow

Do not implement Phase 2 authentication business functionality yet.

At the end, list created/modified files, commands executed, backend and frontend validation results, remaining Phase 1 issues, and the exact instruction required to begin Phase 2.
