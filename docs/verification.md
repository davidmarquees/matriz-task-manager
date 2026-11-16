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
