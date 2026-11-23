# API conventions

- Base path `/api/v1`. Correct HTTP methods and status codes. Entities are never returned; use DTOs.
- Collections are paginated: query parameters `page` (0-based, default 0), `size` (default 20, max 100),
  `sort` and `direction` (`asc`/`desc`) limited to a whitelist per endpoint. Oversized pages or unknown sort
  fields return `400`.

```json
{
  "content": [],
  "page": 0,
  "size": 20,
  "totalElements": 0,
  "totalPages": 0,
  "first": true,
  "last": true
}
```

## Errors (RFC 7807, `application/problem+json`)

```json
{
  "type": "https://school.example/errors/validation",
  "title": "Validation failed",
  "status": 400,
  "detail": "One or more request fields are invalid.",
  "instance": "/api/v1/students",
  "errors": [{ "field": "admissionNumber", "message": "Admission number is required" }],
  "timestamp": "2026-11-19T06:00:00Z",
  "traceId": "..."
}
```

| Status | `type` suffix | Cause |
| ------ | ------------- | ----- |
| 400 | `validation` | Bean validation failure (`errors` lists fields) |
| 400 | `bad-request`, `invalid-request` | Malformed JSON, bad paging/sorting |
| 401 | `unauthorized` | Not authenticated |
| 403 | `forbidden` | Authenticated but not permitted, or missing/invalid CSRF token |
| 404 | `not-found` | Unknown resource or route |
| 405 / 415 | `method-not-allowed` / `unsupported-media-type` | Wrong method or media type |
| 409 | `conflict`, `concurrent-modification`, `data-conflict` | Duplicate or stale (optimistic lock) data |
| 422 | `business-rule-violation` | Domain rule violated (message is safe to show) |
| 429 | `too-many-requests` | Rate limited; `Retry-After` header gives the wait in seconds |
| 500 | `internal-server-error` | Unexpected; generic message, details only in server logs |

Every response carries an `X-Trace-Id` header matching `traceId` in the body and in the server logs.

## Authentication endpoints

All paths are under `/api/v1/auth`. State-changing calls need the `X-XSRF-TOKEN` header (see
[security.md](security.md)). Tokens travel only in `HttpOnly` cookies.

| Method and path | Auth | Success | Notes |
| --------------- | ---- | ------- | ----- |
| `POST /login` | none | `200` user | `{identifier, password}`; `401` generic, `429` throttled |
| `POST /refresh` | refresh cookie | `200` user | Rotates the token; `401` clears both cookies |
| `POST /logout` | refresh cookie | `204` | Works even with an expired access token |
| `GET /me` | access cookie | `200` user | `{id, schoolId, username, email, firstName, lastName, roles[]}` |
| `POST /change-password` | access cookie | `204` | `{currentPassword, newPassword}`; `422` wrong current password |
| `POST /forgot-password` | none | `202` | `{email}`; identical answer for unknown emails |
| `POST /reset-password` | none | `204` | `{token, newPassword}`; `400` invalid or expired link |

## Academic configuration endpoints (administrators only)

All under `/api/v1/admin`, scoped to the caller's school (another school's ids answer `404`). Every list is paged
(`page`, `size`, `sort`, `direction`), and every update sends the record's current `version`:
a stale or missing version answers `409` / `400`. Responses never include `schoolId`.

| Resource | Endpoints | Filters (query) | Notes |
| -------- | --------- | --------------- | ----- |
| `/academic-years` | `GET`, `POST`, `GET/PUT/DELETE /{id}`, `POST /{id}/activate`, `POST /{id}/close` | `status`, `search` | One active year (`409` otherwise); closed years are read-only (`422`); delete only a planned year without sections |
| `/grade-levels` | `GET`, `POST`, `GET/PUT/DELETE /{id}` | `active`, `search` | Ordered by `displayOrder`, then name; delete refused while sections use it |
| `/subjects` | `GET`, `POST`, `GET/PUT/DELETE /{id}` | `active`, `search` (code or name) | Code unique per school, case-insensitive, stored upper case |
| `/sections` | `GET`, `POST`, `GET/PUT/DELETE /{id}` | `academicYearId`, `gradeLevelId`, `active`, `search` | `academicYearId` and `gradeLevelId` only on create; `classTeacherId` must be an active teacher |
| `/section-subjects` | `GET`, `POST`, `PUT/DELETE /{id}` | `academicYearId`, `sectionId`, `teacherId`, `subjectId` | Assigns a subject (and optional teacher) to a section; one assignment per subject and section |
| `/teacher-options` | `GET` | `search` | Active teachers (name, email) for pickers |

Rule violations return `422` with a message that is safe to show; duplicates and in-use deletes return `409`.
Allowed `sort` fields are whitelisted per endpoint (see the OpenAPI document in development).

## People and enrollment endpoints (administrators only)

Same conventions as above (school scoping, paging, `version` on updates). Optional text that is sent must be
valid, so clients send `null` instead of an empty string for blank phone, blood group and similar fields.

| Resource | Endpoints | Filters (query) | Notes |
| -------- | --------- | --------------- | ----- |
| `/students` | `GET`, `POST`, `GET/PUT /{id}` | `status`, `search` (name, admission number, email) | `POST` creates the profile and the account (`admissionNumber`, `password` required; `409` for a duplicate number or email, `400` field errors). `PUT` ignores both; `status` `WITHDRAWN` / `GRADUATED` ends the active enrollment. Responses include `currentEnrollment` |
| `/students/{id}/enrollments` | `GET` | | History, newest first |
| `/students/{id}/guardians` | `GET`, `POST`, `PUT/DELETE /{linkId}` | | Link an existing guardian; exactly one primary (`409` when asked to clear the only primary) |
| `/guardians` | `GET`, `POST`, `GET/PUT/DELETE /{id}` | `search` | Delete refused (`409`) while linked; responses include `studentCount` |
| `/teachers` | `GET`, `POST`, `GET/PUT /{id}` | `status` (employment), `search` | `POST` creates profile and account (`employeeId`, `password`); non-active teachers cannot be assigned to sections or subjects |
| `/enrollments` | `GET`, `POST` | `academicYearId`, `gradeLevelId`, `sectionId`, `status`, `search` | `POST` `{studentId, sectionId, rollNumber?, enrollmentDate?}`: `422` for a full, inactive or closed-year section or an inactive student, `409` for a second active enrollment in the year or a taken roll number |
| `/enrollments/{id}/transfer` | `POST` | | `{targetSectionId, rollNumber?}`, same year only; the old row becomes `TRANSFERRED` |
| `/enrollments/{id}/withdraw`, `/complete` | `POST` | | Ends an active enrollment |
| `/promotions` | `POST`, `GET` | | `POST` `{sourceAcademicYearId, targetAcademicYearId, items[{studentId, targetSectionId}]}`, up to 500 items, answers `201` with per-student `PROMOTED` / `FAILED` results and a reason; request-level problems (same year, target not later or closed, duplicate students) are `422`. `GET` is the run history |
| `/users` | `GET` | `role`, `status`, `search` | Role names are `ROLE_ADMIN`, `ROLE_TEACHER`, `ROLE_STUDENT` |
| `/users/admins` | `POST` | | Creates an administrator (email is the sign-in ID) |
| `/users/{id}/status` | `POST` | | `{status, version}` with `ACTIVE`, `SUSPENDED` or `ARCHIVED`; `422` for your own account; `409` for the last active administrator; suspending or archiving revokes the person's sessions |
| `/users/{id}/unlock` | `POST` | | Clears a sign-in lockout |

## Timetable and attendance endpoints

Same conventions as above. Dates are `YYYY-MM-DD`, times `HH:mm`, weekdays are `MONDAY`..`SUNDAY`.

**Administrators** (`/api/v1/admin`):

| Endpoint | Filters (query) | Notes |
| -------- | --------------- | ----- |
| `GET/POST /timetable-entries`, `GET/PUT/DELETE /timetable-entries/{id}` | `academicYearId`, `sectionId`, `teacherId`, `day`, `status` | `sectionId` only on create. The subject must be assigned (active) to the section (`422`); `teacherId` defaults to the assigned teacher and must be an active teacher; missing dates mean the whole academic year, and dates outside the year are `400`. Clashes of class, teacher or room answer `409`. A closed year is read-only (`422`) |
| `GET /timetable/sections/{id}`, `GET /timetable/teachers/{id}` | `date` | The Monday-to-Sunday week containing `date` (default today): `{weekStart, weekEnd, entries[]}`; each entry carries its calendar `date`, and slots outside their effective dates are left out |
| `GET /attendance/sessions` | `sectionId`, `from`, `to`, `type`, `status` | Newest first, with per-status counts and the number of enrolled students |
| `GET /attendance/sessions/{id}` | | Roster merged with the marks (`status` is null while unmarked) |
| `POST /attendance/sessions/{id}/corrections` | | `{studentId, status, remarks?, reason}`, reason 5-500 characters. Works on open and finalized sessions; `422` when nothing would change or the student is not enrolled |
| `GET /attendance/sessions/{id}/corrections` | | Permanent history: previous and new status, reason, who, when |
| `GET /attendance/reports/monthly` | `sectionId` (required), `month` (`YYYY-MM`), `type`, `subjectId` | A row for every enrolled student; `percentage` is null when nothing counts |
| `GET /attendance/reports/below-threshold` | `from`, `to` (default: this month so far), `sectionId`, `type`, `subjectId`, `threshold` (1-100, default 75) | Lowest first, at most 500 rows (`truncated`), periods up to one year |

**Teachers** (`/api/v1/teacher`) work on their own classes only; another teacher's class answers `403`:

| Endpoint | Notes |
| -------- | ----- |
| `GET /timetable?date=` | Their own week |
| `GET /attendance/classes` | Classes and subjects they may take attendance for (`daily` is true for the class teacher) |
| `POST /attendance/sessions` | `{sectionId, date, subjectId?, periodNumber?}`; no subject means the daily register. `201` when created, `200` when it already exists. Date rules: not in the future, inside the academic year, within the back-dating limit (`422`) |
| `GET /attendance/sessions`, `GET /attendance/sessions/{id}` | Sessions they opened; one session with its roster |
| `PUT /attendance/sessions/{id}/records` | `{records[{studentId, status, remarks?}]}` (up to 500). Upserts; `422` for students not enrolled in the class, duplicates, or a finalized session |
| `POST /attendance/sessions/{id}/finalize` | `422` until every enrolled student is marked |
| `DELETE /attendance/sessions/{id}` | Only while not finalized |
| `GET /attendance/reports/monthly` | `sectionId`, `month`, `type`, `subjectId`: the class teacher sees the class, a subject teacher only their subject |

**Students** (`/api/v1/student`) have no ids in their paths: `GET /timetable?date=` (the class they are enrolled in)
and `GET /attendance?month=` (their own finalized marks, with daily and subject-wise rates).

Session writes lock the session row, so marking and finalizing cannot interleave, and timetable writes take a
per-school advisory lock, so two requests cannot both pass the clash check.

## Assignment endpoints

Same conventions as above (paging, `version` on updates, `422` for rule violations, `409` for stale versions).
Instants are ISO-8601 UTC. Teachers only reach their own assignments and students only their own class, as
described in [security.md](security.md). File uploads are `multipart/form-data` with repeated `files` parts;
downloads return the bytes as an attachment.

**Teachers** (`/api/v1/teacher`):

| Endpoint | Notes |
| -------- | ----- |
| `GET /assignments` | Filters `sectionId`, `status`, `search`; archived work is hidden unless `status=ARCHIVED`. Each item has `counts` (enrolled, submitted, awaitingReview, reviewed) |
| `POST /assignments` | `{sectionId, subjectId, title, description?, dueAt, maxMarks, allowLate}`; creates a `DRAFT`. `403` unless the teacher teaches that subject in that class in the active year; `422` for a past due date |
| `GET/PUT /assignments/{id}` | `PUT` needs `version`; section and subject cannot change; `maxMarks` cannot change once submissions exist (`422`); `ARCHIVED` is read-only |
| `POST /assignments/{id}/publish`, `/close`, `/reopen`, `/archive` | `DRAFT` to `PUBLISHED` to `CLOSED`; `reopen` needs a future due date or `allowLate`; `archive` from `CLOSED` only. Other transitions answer `422` |
| `DELETE /assignments/{id}` | Drafts only (`422` otherwise); stored files are removed |
| `POST /assignments/{id}/attachments` | `files` (up to 10 in total on the assignment, `422` beyond that). Only while `DRAFT` or `PUBLISHED`. Bad type, signature, size or empty file: `400` on field `files` |
| `DELETE /assignments/{id}/attachments/{attachmentId}` | Same status rule |
| `GET /assignments/{id}/attachments/{attachmentId}/download` | |
| `GET /assignments/{id}/submissions` | Roster: every enrolled student with their submission status, even when nothing was handed in |
| `GET /assignments/{id}/submissions/{submissionId}` | Includes the written answer and the files |
| `GET /assignments/{id}/submissions/{submissionId}/attachments/{attachmentId}/download` | |
| `PUT /assignments/{id}/submissions/{submissionId}/review` | `{outcome, marks?, feedback?, version}`. `REVIEWED` needs `marks` from 0 to `maxMarks` with at most two decimals (`400`); `RESUBMISSION_REQUESTED` needs `feedback` and takes no marks. Allowed while the assignment is `PUBLISHED` or `CLOSED`; a stale `version` answers `409` |
| `GET /submissions` | Review queue. Defaults to `pending=true` (`SUBMITTED` and `LATE`, oldest first); an explicit `status` replaces that default; also `assignmentId` |

**Students** (`/api/v1/student`), published and closed assignments of their current class only:

| Endpoint | Notes |
| -------- | ----- |
| `GET /assignments` | Filters `status` (`PUBLISHED`, `CLOSED`), `search`. Each item carries the student's own submission and `canSubmit` |
| `GET /assignments/{id}` | `404` for drafts, archived work and other classes |
| `POST /assignments/{id}/submission` | Multipart: `textAnswer?` and `files` (up to 5 on the submission). Creates or replaces the answer; needs text or a file (`400`). After the due date the work becomes `LATE` if `allowLate`, otherwise `422`; a closed assignment answers `422`; reviewed work cannot be changed (`422`); after `RESUBMISSION_REQUESTED` the student may answer again at any time and the work is not late |
| `DELETE /assignments/{id}/submission/attachments/{attachmentId}` | Same rules as submitting |
| `GET /assignments/{id}/attachments/{attachmentId}/download`, `GET /assignments/{id}/submission/attachments/{attachmentId}/download` | The teacher's files and the student's own files |
| `GET /submissions` | Their own submissions with status and, once reviewed, marks |

A request larger than the multipart limits (10 MB per file, 55 MB per request) is refused by Spring with `413`
before it reaches the controllers.

## Examination endpoints

Same conventions as above (paging, `version` on updates, `422` for rule violations, `409` for stale versions or
duplicates). Marks, percentages and totals are decimals with two places. Exam statuses are `DRAFT`,
`OPEN_FOR_MARKS`, `LOCKED` and `PUBLISHED`; only administrators move an exam between them.

**Administrators** (`/api/v1/admin`):

| Endpoint | Notes |
| -------- | ----- |
| `GET/POST /grade-rules`, `PUT/DELETE /grade-rules/{id}` | The school's grade scale: `{grade, description?, minPercent, maxPercent}`, inclusive ranges with at most two decimals. `409` when a band overlaps another or a grade name repeats (ignoring case); `400` when min is above max |
| `GET /exams` | Filters `academicYearId`, `gradeLevelId`, `status`, `search`; sort `name`, `status`, `startDate`, `createdAt` |
| `POST /exams`, `GET/PUT/DELETE /exams/{id}` | `{academicYearId, gradeLevelId, name, startDate?, endDate?, subjects[{subjectId, maxMarks, passMarks, examDate?}]}`. Only in the **active** year (`422`); 1-30 distinct active subjects, pass marks not above maximum, exam dates inside the exam dates (`400` on field `subjects`); name unique per grade and year (`409`). `PUT` and `DELETE` are for drafts only (`422`) |
| `POST /exams/{id}/open` | `DRAFT` to `OPEN_FOR_MARKS`; `422` while no class of the grade is taught any of the subjects |
| `POST /exams/{id}/lock` | `OPEN_FOR_MARKS` to `LOCKED`. `422` unless every enrolled student has a mark (or an absence) for every subject their class takes, there is at least one student, and the grade scale covers 0 to 100 without gaps. Waits for marks that are being saved |
| `POST /exams/{id}/reopen` | `LOCKED` back to `OPEN_FOR_MARKS`; not possible once published |
| `POST /exams/{id}/publish` | `LOCKED` to `PUBLISHED`: re-checks completeness, freezes every student's result, and cannot be undone |
| `GET /exams/{id}/progress` | Per subject and class: expected and entered counts, teacher, and the total `missing` |
| `GET /exams/{id}/results?sectionId=` | One class's results; a **preview** (`preview: true`) while locked, the frozen copy once published; `422` before the exam is locked |
| `GET /exams/{id}/results/{studentId}` | One student's report card |

**Teachers** (`/api/v1/teacher/exams`), for subjects and classes they teach (`403` otherwise; draft exams are `404`):

| Endpoint | Notes |
| -------- | ----- |
| `GET /` | Exams with work for the teacher: `tasks[]` (subject, class, expected and entered counts) and `commentSections[]` (classes they are the class teacher of) |
| `GET /{examId}/marks?examSubjectId=&sectionId=` | The marks sheet; `editable` is true only while the exam is open |
| `PUT /{examId}/marks?examSubjectId=&sectionId=` | `{entries[{studentId, marks?, absent, remarks?}]}`, up to 500. Marks from 0 to the subject's maximum with at most two decimals; an absent student must have no marks; an entry with no marks that is not absent clears the mark. The whole request is rejected (`400`, fields such as `entries[2].marks`) if any entry is invalid, and with `422` when the exam is not open |
| `GET /{examId}/comments?sectionId=` | Overall comments for a class (class teacher only, `403` otherwise) |
| `PUT /{examId}/comments/{studentId}` | `{comment}` up to 500 characters; blank removes it; only while the exam is open |

**Students** (`/api/v1/student/results`): `GET /` lists their **published** results (`examName`, `percentage`,
`grade`, `result`), `GET /{examId}` is their report card (`404` unless published). Nothing is returned for
exams that are not published, and a student never supplies an id for themselves.

How results are worked out: the total adds the marks of every subject the student's class takes (an absent
subject scores 0 but its maximum still counts), the percentage is rounded half up to two decimals and graded by the
scale, a subject is passed when marks reach its pass marks, and the student passes when every subject is passed.

## Announcement endpoints

**Reading** (`/api/v1/announcements`, any signed-in role): `GET /` is the caller's feed, newest first (`unread=true`
for the unread ones), `GET /unread-count`, `GET /{id}` and `POST /{id}/read` (idempotent, `204`). An announcement
is in someone's feed only when it is `PUBLISHED`, started (`publishAt` empty or past), not expired (`expiresAt`
empty or future) and meant for them: `ALL` for everyone, `ADMINS`, `TEACHERS`, `STUDENTS` by role, and `SECTION`
for the students enrolled in that class and the teachers who teach it or are its class teacher. Anything else
answers `404`.

**Writing** (`/api/v1/admin/announcements` for administrators and the same operations under
`/api/v1/teacher/announcements` for teachers): `GET` (administrators: the whole school; teachers: their own),
`POST` (draft), `GET/PUT/DELETE /{id}`, `POST /{id}/publish`, `POST /{id}/archive`. A teacher may only write
`SECTION` announcements for a class they teach (`403`). `sectionId` is required for `SECTION` and rejected for the
other audiences (`400`). Drafts can change audience; once published the audience is fixed (`422`) and the
announcement can only be edited or archived, never deleted. `expiresAt` must be after `publishAt` (`400`) and a
draft that has already expired cannot be published (`422`). Administrators also see `readCount`.

## Fee endpoints (administrators and the student concerned)

Money is `numeric(12,2)`; amounts must be positive with at most two decimals (`400`). Teachers have **no** fee
endpoints and are refused (`403`) on every `/admin` fee path.

| Endpoint | Notes |
| -------- | ----- |
| `GET/POST /admin/fee-categories`, `PUT/DELETE /admin/fee-categories/{id}` | Names unique ignoring case (`409`); delete refused while structures use it |
| `GET/POST /admin/fee-structures`, `GET/PUT/DELETE /admin/fee-structures/{id}` | What a grade pays for a category in a year and when (`amount`, `dueDate`); unique per year, grade, category and due date. After fees have been generated only the description and the active flag change (`422`) and the structure cannot be deleted (`409`) |
| `POST /admin/fee-structures/{id}/generate` | Bills every student currently enrolled in the grade who has no bill from this structure; returns `{created, alreadyBilled}`; repeatable; active academic year only |
| `GET /admin/student-fees` | Filters `academicYearId`, `gradeLevelId`, `feeCategoryId`, `studentId`, `status`, `search` (name or admission number) |
| `GET /admin/student-fees/{id}` | The bill with its payments, reversed ones included |
| `POST /admin/student-fees/{id}/payments` | `{amount, method, reference?, paidOn}`; `201` with the payment and its receipt number. `422` when it exceeds the balance or the fee is waived; `400` for a future date. Methods: `CASH`, `CHEQUE`, `BANK_TRANSFER`, `UPI`, `CARD`, `OTHER` |
| `POST /admin/student-fees/{id}/waive`, `/unwaive` | `{reason}` of 5-300 characters; a fee with payments cannot be waived (`422`) |
| `GET /admin/payments` | Filters `from`, `to`, `method`, `studentId`, `reversed`, `search` (receipt, name or admission number) |
| `GET /admin/payments/{id}`, `GET /admin/payments/{id}/receipt` | One payment; the printable receipt data |
| `POST /admin/payments/{id}/reverse` | `{reason}`; the payment stays, the fee's balance is restored; `409` if already reversed |
| `GET /admin/fee-reports/summary?academicYearId=&gradeLevelId=` | Billed (waived fees excluded), collected, outstanding, overdue, waived, counts per status and totals per category |
| `GET /admin/fee-reports/collections?from=&to=` | Money received in a period (default: this month, at most a year), net of reversals, per payment method |
| `GET /student/fees`, `GET /student/fees/{id}`, `GET /student/payments/{id}/receipt` | The signed-in student's own bills, payments and receipts; anything else is `404` |

Fee statuses (`NOT_DUE`, `DUE`, `PARTIALLY_PAID`, `PAID`, `OVERDUE`, `WAIVED`) are worked out from the facts and
today's date in the school's time zone, never stored, so they cannot go stale: waived; paid in full; overdue (past
the due date with a balance); partially paid; due (nothing paid and due within `FEE_DUE_SOON_DAYS`, default 14);
otherwise not due.

## Dashboard and audit endpoints

| Endpoint | Notes |
| -------- | ----- |
| `GET /api/v1/admin/dashboard` | Active students, teachers and classes, today's attendance registers (finalized and open), the fee snapshot of the active year (`null` without one), exams by stage, live announcements and the latest audit entries |
| `GET /api/v1/teacher/dashboard` | Today's periods, classes taught, submissions awaiting review, marks still to enter, classes whose daily register is not opened yet, unread announcements |
| `GET /api/v1/student/dashboard` | Class, today's periods, this month's attendance percentage (`null` until a register is finalized), upcoming work, latest published result, outstanding fees and unread announcements |
| `GET /api/v1/admin/audit-logs` | Filters `action`, `entityType`, `entityId`, `actorId`, `from`, `to` (dates in the school's time zone), `search` (summary text); newest first; sort `createdAt`, `action`, `entityType` |
| `GET /api/v1/admin/audit-logs/{id}`, `/actions`, `/entity-types` | One entry with its sanitized before and after values; the distinct actions and entity types, for filters |

The audit trail is read-only: `POST`, `PUT` and `DELETE` on these paths answer `405`. Dashboards only read.

The OpenAPI document (`/v3/api-docs`) and Swagger UI (`/swagger-ui.html`) describe every endpoint, its role and the
two authentication pieces (the `access_token` cookie and the `X-XSRF-TOKEN` header); both are disabled in `prod`.

## Backend building blocks

- Throw `ResourceNotFoundException`, `ConflictException`, `BusinessRuleException`, `InvalidRequestException`,
  `UnauthorizedException` or `TooManyRequestsException` from services; `GlobalExceptionHandler` maps them.
- Build pageables with `PageRequestFactory` and return `PageResponse.from(page, mapper)`.
- Unknown JSON properties are rejected (`fail-on-unknown-properties`), so clients cannot inject `schoolId` or
  `status`.
- Use `@AuthenticationPrincipal AuthenticatedUser` in controllers to get the caller; never trust ids sent by
  the client for ownership decisions. Repositories are queried with the caller's `schoolId`.
- Search with `SearchSpecs` (escapes `%` and `_`) and `JpaSpecificationExecutor`; avoid `(:param is null or ...)`
  JPQL with UUID parameters, which PostgreSQL cannot type.
- Record important changes with `AuditEventPublisher.record(actor, action, entityType, id, summary)`.
- Use `OptimisticVersion.check(request.version(), entity.getVersion())` for updates.

## Documentation

OpenAPI JSON at `/v3/api-docs` and Swagger UI at `/swagger-ui.html` (development only).
