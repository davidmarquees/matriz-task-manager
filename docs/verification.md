# Verification — Matriz

Verified scenarios, one row each: what was expected, what was observed and the evidence.
Type: **Auto** = automated test · **Manual** = command or check run by hand.
Worksheet 03, Ex. 8 scenarios (01 to 09) are added when that exercise is done.

## Increment 1 — Skeleton and persistence (Ex. 2), 2026-11-16

Environment: macOS, Java 21.0.9, Maven Wrapper 3.9.12, Node 26, Docker 29.1.3, PostgreSQL 16 (container).

| # | Scenario | Type | Expected | Observed | Evidence |
|---|---|---|---|---|---|
| V-01 | Backend build and tests | Auto | All tests pass on an isolated database | 16 tests, 0 failures, `BUILD SUCCESS` | `cd backend && ./mvnw verify`: `SchemaMigrationTest` (10), `DevSeedTest` (5), `HealthEndpointTest` (1) |
| V-02 | Schema and constraints | Auto | Flyway applies V1 and V2; duplicate email (any case), title only of spaces, assignee who is not a member and duplicate assignee are refused; task defaults TODO / MEDIUM; deleting a project leaves no tasks, assignees, comments or members | As expected | `SchemaMigrationTest` |
| V-03 | Append-only logs (D39) | Auto + Manual | The application user cannot change or delete log entries | `ERROR: permission denied for table activity_log` | `SchemaMigrationTest`; `psql -U matriz_app -c "delete from activity_log"` in the Compose database |
| V-04 | Demo data (A25) | Auto | 2 demo users without a forced password change, password stored only as BCrypt; Manager in 2 projects, Member in 1; tasks in every status; Admin must change the password | As expected | `DevSeedTest` |
| V-05 | Start-up against PostgreSQL, twice | Manual | Health `UP`; first start applies 3 migrations and creates the Admin; second start changes nothing | Run 1 and run 2: `{"status":"UP"}`, `users=3 projects=2 members=3 tasks=6 assignees=3 audit=6 flyway=1,2,2.1`; run 2 log: `Schema "public" is up to date. No migration necessary.` | `docker compose up -d`; `java -jar target/matriz-backend-0.1.0-SNAPSHOT.jar` twice; row counts with `psql` in the container |
| V-06 | Frontend build and proxy | Manual | `npm run build` passes; the dev server serves the page and forwards `/api` to the backend | Build OK (27 modules); `curl localhost:5173/api/actuator/health` → `{"status":"UP"}` | `cd frontend && npm run build`; `npx vite` + `curl` |

Not checked in this increment: the page in a real browser (only fetched with `curl`), and running the README steps from a clean clone (planned for Ex. 9).

## Increment 2 — Authentication and access rules (Ex. 3), 2026-11-16

| # | Scenario | Type | Expected | Observed | Evidence |
|---|---|---|---|---|---|
| V-07 | Backend build and tests | Auto | All tests pass | 34 tests, 0 failures, `BUILD SUCCESS` | `cd backend && ./mvnw verify` (new: `AuthFlowTest` 12, `SessionCookiesTest` 1, `LoginRateLimitTest` 1, `ProjectAccessTest` 4) |
| V-08 | Valid and invalid credentials (US1, D6) | Auto + Manual | Valid login → 200 with the user; wrong password and unknown email → the same 401 body | As expected; both 401 bodies are identical and say "Invalid email or password." | `AuthFlowTest`; `curl` flow below |
| V-09 | Requests without authentication (D8) | Auto + Manual | 401 Problem JSON; the CSRF cookie is set (`Path=/`, `SameSite=Lax`) | `HTTP/1.1 401`, `Set-Cookie: XSRF-TOKEN=…; Path=/; SameSite=Lax`, body `{"type":"about:blank","title":"Unauthorized","status":401}` | `curl -i localhost:8080/api/auth/me`; `SessionCookiesTest` |
| V-10 | Session lifecycle with cookies | Auto + Manual | me 401 → login 200 (new HttpOnly session cookie and new CSRF token) → me 200 → logout 204 → me 401; login without CSRF token → 403 | As expected | `curl` with a cookie jar: `401, 401 (wrong password), 200, 200, 204, 401`; `SessionCookiesTest`, `AuthFlowTest` |
| V-11 | Lockout, deactivation, rate limit (D7, D8) | Auto | 5 failures lock the account (correct password refused, same message, `ACCOUNT_LOCKED` audited); success resets the counter; a deactivated account loses its open session; 4th attempt from one IP with a limit of 3 → 429 with `Retry-After`, another IP still works | As expected | `AuthFlowTest`, `LoginRateLimitTest` |
| V-12 | Password change rules (D45 to D48) | Auto | Forced change blocks other operations (403) until done; wrong current password → 422 `WRONG_PASSWORD` and counts as a failure; same as current → 422 `SAME_AS_CURRENT`; short → 422 `TOO_SHORT`; success changes the session id, ends the other session, writes `PASSWORD_CHANGED` | As expected | `AuthFlowTest` |
| V-13 | Interface (browser) | Manual | Without a session → login page; wrong password → generic message and the email is kept; correct password → home page with name and role; reload keeps the session; same new password → clear message; backend restart → next request goes to login with "A sessão expirou"; log out → login page and `/` redirects to login | All as expected | Built-in browser on `http://localhost:5173` with the demo accounts (backend jar + Vite dev server + Compose database) |
| V-14 | Two users, different access (Ex. 3.5) | Auto | Member gets 404 on "Annual report" (not a member) and 403 on Manager actions in "Website redesign"; Manager is Manager of both; unknown project → 404 | As expected | `ProjectAccessTest` (demo data) |

Not checked in this increment: the 30-minute inactivity timeout (deferred, see docs/ai-usage.md #2); project endpoints using `ProjectAccess` over HTTP (they arrive in Ex. 4).
