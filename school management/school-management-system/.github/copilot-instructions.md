# Copilot instructions for school-management-system

Modular monolith: `backend/` (Java 17, Spring Boot 3.x, Maven wrapper, PostgreSQL, Flyway) and `frontend/`
(React, strict TypeScript, Vite, MUI, TanStack Query, React Hook Form, Zod). Base package
`com.schoolmanagement`.

## Rules

- Flyway owns the schema; never edit a released migration. Hibernate uses `ddl-auto=validate`.
- Use Jakarta packages, UUID keys, `Instant`/`LocalDate`/`LocalTime`, `BigDecimal` for money, `@Version`
  where concurrent edits can lose data.
- Never expose JPA entities; use request/response DTOs, validate all input, keep transactions and business
  rules in services (not controllers or React components), paginate collections (max size 100).
- Errors are RFC 7807 problems via `GlobalExceptionHandler`; never leak stack traces, SQL or secrets.
- Authentication uses HttpOnly cookies + CSRF; never store tokens in localStorage/sessionStorage; never log
  passwords, tokens or reset links. Authorization is decided by the backend on every request (the user is
  reloaded from the database); frontend guards are UX only. Auth failures must stay generic.
- Integration tests use Testcontainers PostgreSQL, never H2. Do not weaken security to make tests pass.
- Frontend: no `any`, TanStack Query for server state, one Axios instance (`src/api/client.ts`), loading,
  empty, error and validation states, 44px touch targets, no horizontal overflow at 320px.
- Do not commit secrets, `.env`, build output or uploads. Work one approved phase at a time.

Run before finishing: `backend/mvnw verify`; in `frontend`: `npm run typecheck`, `npm run lint`,
`npm run format:check`, `npm test`, `npm run build`.
