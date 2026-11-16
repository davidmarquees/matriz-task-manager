# Implementation Plan — Matriz (MVP)

> Legend: **[D]** confirmed decision · **[A]** assumption (to be confirmed) · **[P]** pending decision · ✅ approved in this plan (date).
> Sources: [requirements.md](requirements.md), [user-stories.md](user-stories.md), [constraints.md](constraints.md), [architecture.md](architecture.md), [database.md](database.md), [../openapi.yaml](../openapi.yaml), [ui/wireframe.md](ui/wireframe.md) and the Worksheet 03 scope.
> Status: **approved** by David on 2026-11-16 (Worksheet 03, exercise 1). The status column in section 8 is updated as increments are approved, implemented and verified.

## 1. Way of working

- One increment per exercise (Ex. 2 to Ex. 9). For each: short analysis and plan → approval → implementation of that increment only → real checks (build, tests, start-up) → diff summary → proposed commit message (Conventional Commits). The developer commits.
- Work on the local branch `feat/mvp`; never on `main`. If an increment fails, the preferred recovery is to go back to the last commit.
- **Contract first:** the full contract for the Worksheet scope (section 6) is completed in `openapi.yaml` during Ex. 1 (increment 0), as Worksheet 03 Ex. 1.4 asks, and validated with a linter before any code. Later increments implement it; if implementation shows the contract must change, the change is proposed and approved first.
- Configuration (database URL, API URL, secrets) only through environment variables; the repository has a `.env.example` with fictitious values.
- Every verified scenario is added to `docs/verification.md` (expected, observed, evidence). Every corrected or rejected AI suggestion is added to `docs/ai-usage.md` (request, decision, verification).
- Preparing Worksheets 04 and 05: JUnit 5 tests; integration tests against an isolated database (Testcontainers); stable `data-testid` attributes on login, create project, create task and lists (Playwright E2E).

## 2. Documented MVP vs. Worksheet 03 scope

| Area | Worksheet 03 minimum | Documented MVP | Gap / action |
|---|---|---|---|
| Authentication | Log in, log out, read the authenticated user | FR1, FR1e, A17, A18; session cookie [D] | Add `GET /auth/me`, login and logout to the contract (G1) |
| Projects | Create, list, read, edit, delete authorised projects | FR2–FR4 | Covered |
| Project search | Search projects by name | Not documented (FR10 searches tasks only) | New rule: add to FR10 and US6 (G2) |
| Tasks | CRUD with status and priority | FR5–FR7 | Covered; list and delete missing in the contract |
| Assignees | Several eligible users; remove one assignment | FR8, E1 | Add "eligible users" and add/remove assignee operations (G3) |
| Comments | Add and list, author and date | FR11, A14 | Covered; contract missing |
| Filters | Tasks by status and priority | FR10: text, status, priority, assignee | Documented MVP is wider; keep it |
| Dashboard | Accessible projects, counts per status, link to detail | FR9, wireframe | Covered; endpoint missing ([P] in wireframe.md) |
| Demo data | Seed; ≥ 2 users and projects with distinct access; no data wiped at start-up | FR12, A25: demo users only, no demo projects | Add demo projects to the seed (G4) |
| Password recovery, registration | Optional extensions | FR1c (High), no registration [D] | Decide scope (P9) |
| Not in Worksheet 03 | — | Admin account lifecycle (FR1a, FR1b), audit and activity logs (FR1d, FR13), forced password change (FR1e), saved language and theme (NFR1, NFR8) | Decide scope (P9) |

## 3. Gaps and contradictions

Decision status: ✅ = approved by David (date). Documents are updated in increment 0.

| # | Issue | Where | Decision | Status | Documents to update |
|---|---|---|---|---|---|
| G1 | Only `createTask`, `getTask`, `updateTask` exist in the contract | openapi.yaml | Complete the contract for the Worksheet scope in Ex. 1 (section 6) | ✅ 2026-11-16 | openapi.yaml |
| G2 | Project search by name is required but not documented | requirements.md FR10, user-stories.md US6 | Case-insensitive "contains" on the name, within the user's projects | ✅ 2026-11-16 | requirements.md FR10, user-stories.md US6, openapi.yaml |
| G3 | No way to list eligible users for assignment | openapi.yaml | Eligible users = active members of the task's project (FR8, D22); `GET /projects/{id}/members` serves it | ✅ 2026-11-16 | openapi.yaml |
| G4 | Seed has no projects, so distinct access cannot be tested | database.md FR12, A25 | Dev seed: demo Manager and Member, plus 2 projects (one with both users, one with the Manager only) and some tasks | ✅ 2026-11-16 | database.md FR12 row, constraints.md A25 |
| G5 | Seeded demo users would get `must_change_password = true` (default), which blocks every test and E2E run | database.md `users` | Demo users seeded with `false`; only the env-created Admin must change it | ✅ 2026-11-16 | database.md `users`, constraints.md A25 |
| C1 | Duplicate assignee: 409 in database.md D21, 422 in openapi.yaml | both | 409, like the other duplicates (D1, D13) | ✅ 2026-11-16 | openapi.yaml |
| C2 | Project name: Worksheet example uses 3–100 and description ≤ 1,000; docs say name mandatory ≤ 100 (no minimum) and description ≤ 2,000 | database.md, Worksheet 03 Ex. 4 | Keep the documented rules (Worksheet says "according to the adopted OpenAPI") | ✅ 2026-11-16 | openapi.yaml (schemas) |
| C3 | Drivers say FR1–FR12; scope is FR1–FR13 | architecture.md §1, §3 | Change to FR1–FR13 | ✅ 2026-11-16 | architecture.md |
| C4 | README "Current status": design phase | README.md | Update in increment 1 | ✅ 2026-11-16 | README.md (increment 1) |
| C5 | No pagination anywhere, but Worksheet asks to respect it and to show totals of the full data set | openapi.yaml | P7 | ✅ 2026-11-16 | openapi.yaml, database.md §8 |

## 4. Access rules (consolidated, from requirements.md §3 and database.md §6)

| Action | Admin | Project Manager | Project Member | Non-member |
|---|---|---|---|---|
| Create project | No (403) | Manager **account** only | No (403, D9) | — |
| Read project, list members | Yes (names only) | Yes | Yes | 404 (D17) |
| Edit / delete project, manage members | No (403, D11) | Yes | No (403, D10) | 404 |
| Read, create, edit task | No (403) | Yes | Yes | 404 |
| Delete task | No | Yes | Only tasks they created (D23) | 404 |
| Add / remove assignee | No | Yes | Yes | 404 |
| Add / list comments | No | Yes | Yes | 404 |

- Identity always comes from the session: `createdBy`, comment author and project creator are set by the backend; any such field sent by the client is refused (unknown field, 400).
- Project and task ids in the URL are checked against the user's membership on every request (D17, D18), so changing ids in a request never gives access.
- Ambiguity to confirm with the trainer: **who can assign** — the docs allow any member (database.md CRUD table). Kept as documented unless told otherwise.

## 5. Authentication mechanism and session lifecycle [D]

- [D] Server-side session, HttpOnly cookie, Spring Security; BCrypt hashes; 30-minute inactivity timeout (A18); lockout after 5 failures (A17).
- [D] (2026-11-16) `POST /api/auth/login` with JSON `{email, password}` → 200 with the user, new session id (session fixation protection). `POST /api/auth/logout` → 204. `GET /api/auth/me` → 200 or 401.
- [D] (2026-11-16) CSRF: Spring Security cookie `XSRF-TOKEN` read by the SPA and sent as `X-XSRF-TOKEN` (already [A] in openapi.yaml); the cookie is issued on the first request (`GET /auth/me`).
- [D] (2026-11-16) On any 401 the SPA goes to the login page with the message "session expired" (Worksheet Ex. 3.5).
- Cookie `Secure` flag off only in the local profile (HTTP); documented in the README.

## 6. Missing API operations

All operations below, except the optional group, are added to `openapi.yaml` in increment 0 (Ex. 1). The last column says where each is implemented.

| Group | Operations to add | Implemented in |
|---|---|---|
| Auth | `login`, `logout`, `getMe` | 2 |
| Projects | `listProjects` (`?q=` name search), `createProject`, `getProject`, `updateProject` (PATCH + `version`), `deleteProject` | 3 |
| Members | `listMembers` (also the eligible-user list), `addMember`, `changeMemberRole`, `removeMember` | 3 |
| Tasks | `listTasks` (`?q=&status=&priority=&assigneeId=`), `deleteTask`; existing three unchanged | 4 |
| Dashboard | `getDashboard` (per project: counts per status; totals) | 4 |
| Assignees | `addAssignee`, `removeAssignee` | 5 |
| Comments | `listComments`, `addComment` | 5 |
| Optional (P9) | change own password, preferences, accounts (create, deactivate, reactivate, anonymise, set password, appoint Manager), user audit log, project activity log, password reset request and confirm | 8 (contract added then, only if approved) |

## 6a. Data model changes

Changes to [database.md](database.md) needed by this plan. None changes a table's structure; they are added to database.md in increment 0 once approved.

| # | Change | Reason | Status |
|---|---|---|---|
| M1 | Dev seed inserts the demo Manager and Member with `must_change_password = false` | G5: tests and E2E must log in directly | ✅ 2026-11-16 |
| M2 | Dev seed inserts 2 demo projects (one with both demo users, one with the Manager only), their memberships and a few tasks in each status | G4: distinct access; non-empty dashboard | ✅ 2026-11-16 |
| M3 | Seed is a Flyway migration in the `dev` location, so it runs once and never wipes data | Worksheet Ex. 2; A25 | ✅ 2026-11-16 |
| M4 | Index on `lower(projects.name)` is not needed at NFR2 scale; search uses `ILIKE` over the user's projects | G2 | ✅ 2026-11-16 |
| M5 | No new tables or columns for the Worksheet scope; the optional increment uses the existing ones | P9 | ✅ 2026-11-16 |

## 7. Decisions

### Confirmed [D] (architecture.md)
Modular monolith; Java + Spring Boot (REST), React SPA, PostgreSQL; OpenAPI 3.1; session cookie authentication; Problem Details errors; 404 for non-members.

### Approved on 2026-11-16 (recorded in architecture.md in increment 0, as the Worksheet asks)

| # | Decision | Choice | Reason |
|---|---|---|---|
| P1 | Versions | **Java 21 (LTS)** ✅ (David, 2026-11-16), Spring Boot 3.5, React 18, Vite, PostgreSQL 16 | Java 21 is the current LTS and is already installed (OpenJDK 21.0.9); the default `java` on the machine is 17, so the build selects 21 through `JAVA_HOME` (see README in increment 1). The first proposal was Java 17; corrected by the developer and recorded in `docs/ai-usage.md` |
| P2 | Build tools | ✅ Maven (installed); npm | No extra installs |
| P3 | Runtime | ✅ Docker Compose for PostgreSQL only; backend and frontend run locally in development | Simple; full compose in Worksheet 05 |
| P4 | Tests | ✅ JUnit 5 + Spring Boot Test + Testcontainers (PostgreSQL); Vitest + Testing Library in the frontend | Isolated, repeatable DB (Worksheet 04) |
| P5 | Migrations and seed | ✅ Flyway; seed in a `dev` profile location, applied once (no wipe at start-up) | Already [A] in architecture.md |
| P6 | Credentials | ✅ `.env.example` with fictitious values; Admin and demo passwords from environment variables | constraints.md §5 |
| P7 | Pagination | ✅ `page` (0-based) and `size` (default 20, max 100) on `listProjects` and `listTasks`; response `{items, page, size, totalItems}`; dashboard counts are computed over all data, not the page | Worksheet Ex. 5 and Ex. 7 |
| P8 | Repository layout and frontend libraries | ✅ `backend/`, `frontend/`, `docker-compose.yml` at the root; React Router, react-i18next, `fetch` wrapper with the API URL from `VITE_API_URL`; CSS variables for themes | Centralised API address (Ex. 7) |
| P9 | Scope beyond Worksheet 03 | ✅ Core increments 1–7 cover the Worksheet scope plus, cheaply, activity-log writing and pt-PT/en labels; Admin lifecycle, logs reading, forced password change, password reset and saved preferences become optional increment 8, done only if time allows | The documented MVP is wider than the Worksheet; the rest stays documented as planned |

Still pending, outside this plan: SMTP provider (only if password reset is done), log retention, final colour palette.

Every new dependency is listed in its increment and installed only after approval.

## 8. Increments

Status values: Not started · Planned (plan approved) · In progress · Done (verified). The evidence column links to the scenarios in `docs/verification.md` and to the entries in `docs/ai-usage.md` for that increment, plus the commit.

| # | Exercise | Content | Status | Evidence |
|---|---|---|---|---|
| 0 | Ex. 1 | This plan, decisions, full contract, doc fixes | Done (2026-11-16) | Redocly CLI lint: valid, 0 errors, 2 accepted warnings (`localhost` server URL, intended for local development; `info-license-strict`, because a proprietary licence has no SPDX identifier or public URL); local YAML and `$ref` check: 22 operations, no missing references; commit `docs: add implementation plan, complete API contract and reconcile docs with Worksheet 03 scope` |
| 1 | Ex. 2 | Skeleton, persistence, seed | Done (2026-11-16) | [verification.md](verification.md) V-01 to V-06; `./mvnw verify` 16 tests passed; commit pending |
| 2 | Ex. 3 | Authentication and access rules | Done (2026-11-16) | [verification.md](verification.md) V-07 to V-14; `./mvnw verify` 34 tests passed; commit pending |
| 3 | Ex. 4 | Projects and members | Done (2026-11-16) | [verification.md](verification.md) V-15 to V-20; `./mvnw verify` 45 tests passed; commit pending |
| 4 | Ex. 5 | Tasks, filters, dashboard counts (+ extra "last change") | Done (2026-11-16) | [verification.md](verification.md) V-21 to V-26; `./mvnw verify` 54 tests passed; commit pending |
| 5 | Ex. 6 | Assignees and comments | Done (2026-11-16) | [verification.md](verification.md) V-27 to V-31; `./mvnw verify` 60 tests passed; commit pending |
| 6 | Ex. 7 | Complete frontend | Done (2026-11-16) | [verification.md](verification.md) V-32 to V-38; `./mvnw verify` 61 tests, `npm test` 11 tests passed; commit pending |
| 7 | Ex. 8 | Verification, tests, bug fixes | Not started | — |
| 8 | optional | Documented features outside the Worksheet (P9) | Not started | — |
| 9 | Ex. 9 | Review, documentation, delivery | Not started | — |

### Increment 0 — Plan and contract (Ex. 1)
- **Files:** `docs/implementation-plan.md` (new), `openapi.yaml` (operations of section 6, pagination schema, C1, C2), `docs/requirements.md` and `docs/user-stories.md` (G2), `docs/database.md` (section 6a, G4, G5, §8), `docs/constraints.md` (A25), `docs/architecture.md` (C3, approved P1–P9 in §4 and §7).
- **Dependencies:** a contract linter run without installing it in the repository, e.g. `npx @redocly/cli lint openapi.yaml` (needs approval to run, downloads the package).
- **Acceptance:** every operation of the Worksheet scope is in the contract with requests, responses and error codes; every gap and contradiction in section 3 has an approved decision and its documents updated.
- **Checks:** linter with no errors; cross-check of each user story of the scope against the contract operations; `git diff --stat` limited to the files above.

### Increment 1 — Skeleton and persistence (Ex. 2)
- **Components:** `backend/` (Spring Boot, module packages from architecture.md §5, `/api` prefix, Problem Details handler, actuator health), `frontend/` (Vite + React, dev proxy, `VITE_API_URL`), `docker-compose.yml` (PostgreSQL), `.env.example`, README run commands.
- **Database:** Flyway `V1` with all tables, keys, `CHECK`s, unique `lower(email)`, `task_assignees` composite FKs, cascades and indexes from database.md §2; `dev` seed (G4, G5); Admin created at first start from env vars.
- **Java:** `pom.xml` sets `java.version` 21; the build fails early on another JDK (Maven Enforcer `requireJavaVersion [21,)`). Commands run with `JAVA_HOME=$(/usr/libexec/java_home -v 21)`.
- **Dependencies:** spring-boot-starter-web, -data-jpa, -validation, -security, -actuator, flyway, postgresql driver, testcontainers; react, react-dom, vite, vitest.
- **Acceptance:** app starts against PostgreSQL; schema created; seed present; second start keeps data.
- **Checks:** `mvn verify` (Testcontainers test: migration + one constraint per table), `npm run build`, start-up log, restart twice and count rows.
- **As implemented (2026-11-16, approved by David):**
  - Versions: Spring Boot 3.5.16, React 18.3.1, Vite 6.4.6, TypeScript 5.9.3 (frontend in TypeScript), Maven Wrapper 3.9.12; Java package `pt.matriz`.
  - Two database users: owner (Flyway) and application user (runtime) with `INSERT` and `SELECT` only on the logs (`V2__app_user_grants.sql`); the application user is created by `docker/postgres/init/01-create-app-user.sh`.
  - Seed: Java Flyway migration `db/seeddev/V2_1__Demo_data` (version 2.1, so later migrations stay in order), active only with `MATRIZ_PROFILE=dev`.
  - Spring Security is not added yet (increment 2); only `spring-security-crypto` for BCrypt. `spring-boot-testcontainers` and Vitest were not needed yet.
  - The `.env` at the repository root is read by the backend (`spring.config.import`) and by Vite (`envDir`).

### Increment 2 — Authentication (Ex. 3)
- **Components:** security config (session, CSRF, JSON login, 401/403 handlers), lockout, `/auth/me`, membership check helper used by every later service; contract for auth; frontend login page, route guard, session-expired message, header with log out.
- **Acceptance:** US1; D6, D7, D8; Admin bootstrap works; password hashes only.
- **Checks:** integration tests: valid login, wrong password, unknown email (same error), no session → 401, lockout after 5; manual login/logout in the browser.
- **As implemented (2026-11-16, approved by David):**
  - Added to the scope: own password change `POST /auth/password` (FR1e, D46 to D48; ends the other sessions and changes the session id); login rate limit per IP address, 20 attempts in 15 minutes, then 429 with `Retry-After` (in memory); `SameSite=Lax` on both cookies; `Secure` flag from `MATRIZ_COOKIE_SECURE`; generic login messages also in the interface; message when the new password equals the current one (David's request).
  - Contract `openapi.yaml` 0.3.0: `changeOwnPassword`, 429 on `login`, error codes `WRONG_PASSWORD` and `SAME_AS_CURRENT`.
  - Backend: `security` (SecurityConfig, AuthController, AccountStateFilter for D8 and D45, LoginRateLimiter, SPA CSRF handler, session registry), `accounts` (User entity, AccountService with lockout), `audit` (UserAuditLog writer), `projects` (ProjectAccess: 404 for non-members, 403 for Members), `web` (Problem Details for every error).
  - Frontend: central API client (cookies, CSRF header, 401 → "A sessão expirou"), AuthContext, protected routes, login, change-password page, header with log out, temporary home page; texts in pt-PT only (translations in Ex. 7); `react-router-dom` 7.18.5.
  - Deferred: automated test of the 30-minute session timeout.

### Increment 3 — Projects and members (Ex. 4)
- **Components:** projects module (service, repository, controller), members, activity-log writer (same transaction), contract; frontend project list with search, create/edit form, detail, delete confirmation, members screen.
- **Acceptance:** US2; D9–D19 (Admin parts limited to read and appoint Manager); search by name; cascade delete (A11); **first increment demo:** log in, create a project, see it in the list, still there after reload and backend restart.
- **Checks:** integration tests for CRUD, search, pagination, 404 for non-member, 403 for Member, last-Manager rule; manual demo above.
- **As implemented (2026-11-16, approved by David):**
  - Backend: `ProjectService` (list with name search and pagination, create, read, update with version, delete with cascade), `MemberService` (list, add by email, change role, remove; last-Manager rule with a row lock), `ProjectAccess` (member, Manager, reader and lock checks), `ActivityLog` and `UserAuditLog` writers (both logs for membership changes), `TaskCounts` (tasks module, counts per status for the projects module), `PatchBody` and `TextInput` (contract rules for partial updates and text fields), strict JSON types (a number for a text field → 422).
  - The Admin account sees every project and its members (without task counts) and gets 403 on changes; appointing a Manager stays in the optional increment 8.
  - Frontend: Projects page (search, pagination, loading / empty / error states, create form for Manager accounts) and project detail (counts, edit with version conflict message, delete and member removal with confirmation, add member by email, change role), navigation in the header; login returns to the page that asked for it.
  - Bug found and fixed during the browser check: logging in again right after logging out failed (403), because logout removed the CSRF cookie; logout now sends a new token. Regression test in `SessionCookiesTest`.

### Increment 4 — Tasks (Ex. 5)
- **Components:** tasks module (list with filters and pagination, delete), dashboard endpoint; frontend task list in the project detail, filters, create form with field errors, translated labels.
- **Acceptance:** US3, US5, US6 (status and priority filters, text search); D20, D23–D27; lists and counts refresh without reload.
- **Checks:** integration tests for validation (only spaces, length), defaults, delete rule, filters, counts; manual check of refresh.
- **As implemented (2026-11-16, approved by David):**
  - Backend: `TaskService` and `TaskController` (list with text search, status, priority and assignee filters and pagination, newest first; create; read; PATCH with version; delete by a project Manager or the creator), `assigneeIds` rules on create and update (active members only, no duplicates), activity entries for tasks and assignees, `getDashboard` (projects of the user with counts and totals; Admin 403). The Admin account gets 403 on tasks (D11).
  - **Extra feature (David's request, outside the Worksheet): "last change" note.** Contract 0.4.0: `Task.lastChange` from the activity log. Option A approved: every member sees "Alterada hoje / ontem / a dd/mm/aaaa" (or "Criada …"); only project Managers get who changed it and what (A23 and D41 unchanged). Description and assignee values are not repeated in the note. Shown on hover, keyboard focus and tap.
  - Frontend: tasks section in the project page (search, filters, pagination, create form, quick status and priority change per row, counts refreshed without reloading), task page (fields, edit with version-conflict message, delete with confirmation only for Managers or the creator), `LastChangeNote` component. The assignee picker is in Ex. 6; the dashboard screen in Ex. 7.

### Increment 5 — Assignees and comments (Ex. 6)
- **Components:** assignee add/remove, eligible users from members; comments module; frontend assignee selector and comment list on the task detail.
- **Acceptance:** US4, US7; D21, D22; author and date set by the backend; data still there after reopening the task.
- **Checks:** integration tests: duplicate assignee 409, non-member 422, comment only spaces 422, non-member comment 404.
- **As implemented (2026-11-16, approved by David):**
  - Backend: `addAssignee` and `removeAssignee` in `TaskService` (any member; active project members only, 422 `NOT_A_PROJECT_MEMBER`; already assigned 409 `ALREADY_ASSIGNED`, also under concurrency through the primary key; not assigned 404; the task version is not changed); `comments` module (`CommentService`, `CommentController`: list oldest first, add with author and date from the server, 1 to 2,000 characters, no edit or delete); activity entries `ASSIGNEE ADDED/REMOVED` (with `taskId`) and `COMMENT CREATED` (without the text).
  - FR14 "last change" now also counts assignee changes ("Responsáveis alterados"); comments do not count. FR14 and US15 added to requirements.md and user-stories.md at David's request.
  - Frontend: task page with assignees (list, remove, picker of eligible members not yet assigned) and comments (list with author, date and time; form with a character counter, text kept on error).

### Increment 6 — Complete frontend (Ex. 7)
- **Components:** dashboard as in the wireframe, all screens of Ex. 7, shared form and message components, loading/empty/success/error states, no double submit, form data kept on error, responsive layout (360 px), light/dark/system theme, PT/EN, `data-testid`.
- **Acceptance:** full MVP usable in the interface; no mocks left; totals over the full data set.
- **Checks:** Vitest component tests for the main forms; manual run of each screen at desktop and 360 px.
- **As implemented (2026-11-16, approved by David):**
  - Design approved in the design canvas (two rounds of feedback): solid greige background `#E3DED6` with a light-stone board `#F2F0EC`, bento cards with large radii, big-number totals with ↗ buttons, earthy palette (brick `#A8432F` for the main action, pine `#20483A` for done, ochre for in progress, hatching for to do), Anton for page titles and big numbers, Inter for text, IBM Plex Mono for dates and labels; dark theme unchanged from the proposal. Every text pair meets WCAG AA.
  - Dependencies: `i18next`, `react-i18next`, `lucide-react`, `@fontsource/anton`, `@fontsource-variable/inter`, `@fontsource/ibm-plex-mono` (fonts bundled, no external requests); `vitest` 5.1.0 (3.x had critical advisories), `@testing-library/react`, `@testing-library/jest-dom`, `jsdom`. `npm audit`: 0 vulnerabilities.
  - Contract 0.5.0: `PATCH /auth/me/preferences` (language and theme saved in the profile, NFR1, NFR8; audited as PREFERENCES_CHANGED; allowed while a password change is required).
  - Frontend: design tokens with light, dark and auto themes; PT/EN translation files for every text (before login the browser language and system theme, A24); app shell with sidebar (desktop) and ☰ menu (mobile); new dashboard from `getDashboard` (totals, projects with progress bars, overall progress gauge, shortcuts, empty state, Admin message); login, password change, projects, project and task pages restyled; accessible confirmation dialog (focus on the safe choice, Escape, focus trap) instead of `window.confirm`; toasts for success; skeletons, empty and error states everywhere.
  - Kept every earlier `data-testid` except `project-create-success` and `task-create-success` (success now shown as a toast, `toast`); added `menu-button`, `language-*`, `theme-*`, `dashboard-*`, `confirm-*`, `project-create-open`, `task-create-open`.

### Increment 7 — Verification (Ex. 8)
- Scenarios 01–09 of the Worksheet in `docs/verification.md`; at least three automated tests (validation rule, authorisation, create then read from the database) already exist from increments 2–5; bugs reproduced, fixed in a limited change, with a regression test.

### Increment 8 — Optional (P9)
- Only if approved: forced password change and own password change; Admin account area; user audit log and project activity log reading; saved language and theme; password reset with a local test mail server.

### Increment 9 — Review and delivery (Ex. 9)
- Diff review, one separate refactoring if useful, `openapi.yaml` and docs matching the code, AGENTS.md commands, README from a clean clone, AI usage examples, proposed steps to merge `feat/mvp` into `main` and tag the delivered version.

## 9. Definition of done (every increment)

- Endpoints match `openapi.yaml`; permissions enforced in backend services (NFR4); error cases from database.md §6 tested where they apply.
- Every write records its activity entry in the same transaction.
- No credentials or real personal data in code, tests, logs or commits.
- Build, tests and start-up run, with real results reported; what was not checked is stated.
- `docs/verification.md`, this plan's status and affected docs updated.

## 10. Risks

- Most of the contract is new; designing it may raise questions that block an increment.
- Many rules are assumptions [A]; a change after implementation costs rework.
- Concurrency rules (D13, D16, D25) need dedicated tests.
- The documented MVP is wider than the Worksheet; without P9 the schedule may not fit.
