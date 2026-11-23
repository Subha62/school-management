# Deployment

This guide covers building, configuring and operating the system in production. Work through the
[production checklist](#production-readiness-checklist) before the first real deployment.

## Build artefacts

- Backend: `cd backend; ./mvnw -DskipTests package` produces an executable JAR.
- Frontend: `cd frontend; npm ci; npm run build` produces static files in `frontend/dist`.

## Runtime configuration (profile `prod`)

Required environment variables (no defaults): `SPRING_PROFILES_ACTIVE=prod`, `DB_URL`, `DB_USERNAME`,
`DB_PASSWORD`, `CORS_ALLOWED_ORIGINS`, `JWT_SIGNING_SECRET` (at least 32 bytes of randomness; rotating it
signs everyone out) and `FRONTEND_BASE_URL` (origin used in password-reset emails). Authentication cookies are
`Secure` by default; keep `COOKIE_SECURE=true` and serve over HTTPS. Optional: `COOKIE_SAME_SITE` (`Lax`),
`SERVER_PORT`, `DB_POOL_SIZE`, `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM`,
`FILE_STORAGE_PROVIDER` (`local`, the only one so far), `FILE_STORAGE_PATH`, `ATTENDANCE_TEACHER_BACKDATE_DAYS` (default 7) and `ATTENDANCE_THRESHOLD_PERCENT`
(default 75), and `FEE_DUE_SOON_DAYS` (default 14: how close to its due date an unpaid fee shows as *due* rather than
*not due*). Timetable writes, grade-scale writes and receipt numbers rely on PostgreSQL locks (an advisory lock, and
row locks on the exam, the fee and the receipt counter), which work with a single database and any number of backend
instances; no extension is needed. Fee dates ("today", due dates, payment dates) follow the school's time zone
(`schools.timezone`), so set it correctly when creating the school.

### Uploads

Assignment files are stored on disk under `FILE_STORAGE_PATH` as `ab/<random-uuid>.<ext>`; the database holds
only the key, display name, type and size. Limits are 10 MB per file (`app.uploads.max-file-bytes`), 10 files per
assignment and 5 per submission, and the multipart limits in `application.yml` (10 MB per file, 55 MB per
request). Spring answers `413` when a request exceeds them, so a reverse proxy in front must allow at least the
same body size (for nginx, `client_max_body_size 60m` on `/api/`). Mount `FILE_STORAGE_PATH` on a **persistent
volume that is shared by every backend instance** and include it in backups together with the database: the
database rows and the files must be restored from the same point in time. The storage sits behind the
`FileStorage` interface, so object storage (S3-compatible or Oracle) can replace the local provider without
touching the assignment code. Files are not virus scanned.
Inject secrets from a secret manager or the platform, never from the repository.

### First administrator

Set `INITIAL_ADMIN_EMAIL` and `INITIAL_ADMIN_PASSWORD` (must satisfy the password policy; optionally
`INITIAL_SCHOOL_NAME` and `INITIAL_SCHOOL_CODE`) for the first start only. They are ignored once an active
administrator exists; remove them afterwards. Demo accounts are never created outside the `dev` profile with a
local database.

### Multiple instances

Login rate limiting is held in memory per instance. Run a single instance, or replace
`AuthRateLimiter` with a shared store, before scaling out.

## Recommended topology

```text
Internet --HTTPS--> reverse proxy (TLS, HSTS)
                      |-- /        -> static frontend (frontend/dist, SPA fallback to index.html)
                      '-- /api/**  -> backend (port 8080)
```

Serving the SPA and API from one origin keeps cookies first-party and avoids CORS in production. The
`prod` profile sets `forward-headers-strategy: framework`; make sure the proxy sends `X-Forwarded-*`.

## Database

Flyway migrates the schema on startup. Use a dedicated database user, back up before upgrades and never
edit released migrations.

## Operations checklist

- Swagger UI and API docs are disabled in `prod`.
- Expose only `/actuator/health` (and `info`) from the application; keep management ports private.
- Persist the file-storage path (or move to object storage) and the PostgreSQL volume, and back them up together.
- Monitor logs by `traceId`.
- Run a **single backend instance** or replace the in-memory login rate limiter first (see Multiple instances); every
  other lock the system relies on is in PostgreSQL.
- Plan retention for `audit_logs` and back it up with the rest of the database.

## Production readiness checklist

The result of the Phase 9 review, with what the code already guarantees and what only the operator can do.

**Already enforced by the code and covered by tests**

- The `prod` profile has no defaults for the database, CORS origins, the JWT secret (32+ bytes) or the front-end URL,
  and refuses to start without them; cookies are `Secure` by default.
- Every business endpoint refuses anonymous callers (`401`) and the wrong role (`403`); a test sweeps the whole list
  of mapped endpoints, so a new controller that forgets its rules fails the build.
- Every record is scoped to the caller's school; teachers and students only reach what they teach or own; financial
  data is administrator-only.
- Errors never carry stack traces, SQL or internal class names; uploads are validated, renamed and served as
  attachments; the audit trail is append-only and credential-free; Swagger and API docs are off in `prod`.
- Dependencies: `npm audit --omit=dev` reports no vulnerabilities and CI fails on high severity; keep Dependabot or a
  scheduled `mvn versions:display-dependency-updates` / OWASP dependency-check run for the Java side.

**For the operator**

- [ ] Serve the SPA and the API from one HTTPS origin behind a reverse proxy; send `X-Forwarded-*`; enable HSTS; allow
      request bodies of about 60 MB on `/api/` (uploads).
- [ ] Generate `JWT_SIGNING_SECRET` from a secret manager (`openssl rand -base64 48`); never reuse the dev value.
- [ ] Use a dedicated database user; grant it `INSERT` and `SELECT` only on `audit_logs`; back up the database and the
      file-storage volume together and test a restore.
- [ ] Set the school's time zone correctly (fee due dates and "today" use it) and `INITIAL_ADMIN_*` for the first start
      only, then remove them.
- [ ] Expose only `/actuator/health`; keep other management endpoints private.
- [ ] Put a virus scanner in front of the upload storage if uploaders are not trusted.
- [ ] Run the load you expect (peak: marks entry and fee collection) against a copy first; the connection pool is
      `DB_POOL_SIZE`.
- [ ] Ship the logs (`AUDIT` logger included) to central storage and alert on `LOGIN_FAILURE` bursts and `5xx`.
