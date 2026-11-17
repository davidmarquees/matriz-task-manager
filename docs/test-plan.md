# Test Plan — Matriz (Worksheet 04)

> Expected results come from the requirements and acceptance criteria ([requirements.md](requirements.md), [user-stories.md](user-stories.md), [database.md](database.md) §6, [../openapi.yaml](../openapi.yaml)), not from the current behaviour of the code.
> When a test disagrees with the documents, the difference is recorded in [quality-security-review.md](quality-security-review.md) as an application defect, a test problem or an environment limitation. The rule is never changed only to make a test pass.
> This file grows with each exercise.

## 1. Test levels

| Level | Tool | Scope | Needs |
|---|---|---|---|
| **U**: unit | JUnit 5, no Spring context | One class or rule in isolation (e.g. `web/TextInput`) | Java 21 only |
| **I**: integration | JUnit 5, Spring Boot Test, MockMvc, Testcontainers (PostgreSQL 16) | API + security + persistence through the real contract | Docker |
| **C**: component | Vitest, Testing Library (jsdom) | Frontend components with the API mocked | Node |
| **E2E** | Playwright (installed Chrome) + axe-core | User journeys through the running app | Running backend and frontend, demo accounts |

Since 2026-11-17 (Ex. 3, part A): unit tests are named `*Test` and run with Surefire (`./mvnw test`, no Docker); integration tests are named `*IT` and run with Failsafe (`./mvnw verify -DskipUTs`), so that Worksheet 05 can run them as separate CI steps. The 16 Worksheet 03 test classes were renamed from `*Test` to `*IT`; older documents (verification.md) keep the old names. Commands: [README.md](../README.md#tests).

## 2. Scenarios: tasks and permissions (Ex. 1)

★ = essential scenario of Ex. 1 (invalid title, task creation and reading, access without permission).
Status: **Covered** = an existing test checks it · **Partial** = only part of it is checked · **Planned** = no test yet; to be added in the exercise shown.
"Covered" and "Partial" come from reading the tests on 2026-11-17. The tests were not run for this exercise.

### 2.1 Validation

| # | Input | Expected | Source | Level | Associated test | Status |
|---|---|---|---|---|---|---|
| T1★ | Create a task with title `"     "` | 422, `title` / `BLANK`; nothing saved | E5, D20 | U + I | I: `TasksApiIT.refusesInvalidTaskData`, `TaskCreateAndReadIT.aTitleMadeOnlyOfSpacesIsRefusedAndNothingIsSaved`; U: `TextInputTest` | Covered (U passed 2026-11-17) |
| T2★ | Title with 2 characters (`"ab"`) | 422, `title` / `TOO_SHORT` | FR5, E5 | U + I | I: `TasksApiIT.refusesInvalidTaskData`; U: `TextInputTest` | Covered (U passed 2026-11-17) |
| T3★ | Title with 151 characters | 422, `title` / `TOO_LONG` | FR5, E5 | U + I | I: `TasksApiIT.refusesInvalidTaskData`; U: `TextInputTest` | Covered (U passed 2026-11-17) |
| T4★ | Title with exactly 3 and exactly 150 characters; `"  abc  "` | Accepted; stored trimmed as `abc` | FR5, A6 | U | `TextInputTest.acceptsAValidTitle`, `.acceptsTheShortestAndLongestTitles`, `.trimsSpacesBeforeCheckingTheLength` | Covered (passed 2026-11-17) |
| T5★ | Create with `{}` (no title) | 422, `title` / `REQUIRED` | FR5, D20 | U + I | I: `TasksApiIT.refusesInvalidTaskData`; U: `TextInputTest` | Covered (U passed 2026-11-17) |
| T14 | Description with 2,001 characters; status `FINISHED`; priority `URGENT` | 422 with the field flagged | E7, D20 | I | `TasksApiIT.refusesInvalidTaskData` | Covered |
| T23 | PATCH with an invalid title (blank, too short, too long, null) | 422; the stored title is unchanged | D20 | I | `TasksApiIT.anyMemberEditsAndStaleOrEmptyChangesAreHandled` (null only) | Partial |
| T25 | Title made only of Unicode spaces (e.g. U+00A0); title of 150 characters that includes an emoji | Refused as blank ("not only spaces", A6); accepted if it has 150 characters (FR5 counts characters) | FR5, A6 | U | `TextInputTest.refusesATitleMadeOnlyOfNonBreakingSpaces`, `.countsAnEmojiAsOneCharacter` | Covered: regression tests for findings Q-1, Q-2, fixed 2026-11-17 |

### 2.2 Creation and reading

| # | Input | Expected | Source | Level | Associated test | Status |
|---|---|---|---|---|---|---|
| T6★ | Member creates `{title}` in their project, then `GET /tasks/{id}` | 201 with `Location`; status TODO, priority MEDIUM, `createdBy` = session user; GET 200 with the same data; row in the database | US3, A5, FR5 | I | `TasksApiIT.memberCreatesATaskWithDefaultsThatIsSavedAndReadBack`; `TaskCreateAndReadIT.memberCreatesATaskThatIsReturnedSavedAndReadBack` (all fields, `Location`, GET, database, list) | Covered (passed 2026-11-17) |
| T7★ | Log in → open project → create task | The task appears in `task-list` (`task-item`) | US3, Ex. 4 | E2E | `task-create.spec.ts` "a Manager creates a task and sees it in the project list" (labels in pt-PT, "Criada hoje", still listed after a reload); `main-flow.spec.ts` | Covered (passed 2026-11-17) |
| T13 | Create with `createdBy` in the body | 400 (identity comes from the session) | Plan §4 | I | `TasksApiIT.refusesInvalidTaskData` | Covered |
| T15 | PATCH with an old `version` | 409 `VERSION_CONFLICT` | D25 | I | `TasksApiIT.anyMemberEditsAndStaleOrEmptyChangesAreHandled` | Covered |
| T20 | Assignee who is not a member, unknown or deactivated; the same assignee twice | Not a member, unknown or deactivated: 422 `NOT_A_PROJECT_MEMBER`. Already assigned: 409 `ALREADY_ASSIGNED`. Repeated id in `assigneeIds` on create: 422 `DUPLICATE` | FR8, D21, D22 | I | `TasksApiIT.assigneesMustBeActiveProjectMembers`, `AssigneesAndCommentsApiIT.onlyEligibleUsersCanBeAssigned`, `.severalAssigneesWithoutDuplicatesAndRemovableOneByOne`, `ConcurrencyIT.theSamePersonAssignedTwiceAtOnceIsSavedOnce` | Partial (deactivated user not checked) |

### 2.3 Access and permissions

| # | Input | Expected | Source | Level | Associated test | Status |
|---|---|---|---|---|---|---|
| T8★ | User who is not a project member: GET, PATCH, DELETE `/tasks/{id}`; GET and POST `/projects/{id}/tasks` | 404 without content; the database is unchanged | E4, D17 | I | `TasksApiIT.outsidersAndTheAdminCannotReachTasks` | Covered |
| T9★ | Demo Member opens by id a task of "Annual report" (a project where only the demo Manager is a member) | 404; content not shown (IDOR with the two demo accounts) | E4, D17, US8 | I + E2E | `TaskCreateAndReadIT.theDemoMemberCannotReadOrChangeATaskOfAProjectTheyAreNotIn` (GET and PATCH → 404, database unchanged); `main-flow.spec.ts` D9 test (project page) | Covered at API level (passed 2026-11-17); manual check with two demo accounts in Ex. 5 |
| T10★ | Any task endpoint without a session | 401 Problem JSON | US1, D8 | I | `TaskCreateAndReadIT.withoutASessionTaskEndpointsAnswer401` | Covered (passed 2026-11-17) |
| T11 | Admin: GET a task, list a project's tasks | 403 | Plan §4, D11 | I | `TasksApiIT.outsidersAndTheAdminCannotReachTasks` | Covered |
| T12 | Member deletes a task created by someone else; edits it | 403; 200 | D23, A13 | I | `TasksApiIT.onlyManagersOrTheCreatorDeleteTasks`, `.anyMemberEditsAndStaleOrEmptyChangesAreHandled` | Covered |
| T16 | POST, PATCH or DELETE on tasks without the CSRF token | 403 | README "Session lifecycle" | I | — (only `AuthFlowIT.loginWithoutCsrfTokenIsRefused`) | Planned (Ex. 5) |
| T17 | GET or PATCH a deleted task | 404 | D26 | I | `TasksApiIT.onlyManagersOrTheCreatorDeleteTasks` (GET only) | Partial |
| T18 | Member removed from the project, with the session still open, reads or edits a task | 404 on the next request | D18 | I | `ProjectsApiIT.removingAMemberRemovesTheirAssignmentsButKeepsTheTasks` (project, after a new login) | Partial |
| T19 | Task creator removed from the project tries to delete their task; a Manager deletes it | 404 for the former member; 204 for the Manager | D24 | I | — | Planned (Ex. 3 or Ex. 5) |
| T27 | GET a task or POST to a project that does not exist | 404, same body as for a foreign task (apart from `instance`); nothing saved | E4, D17 | I | `TaskCreateAndReadIT.aMissingTaskOrProjectLooksTheSameAsAForeignOne` | Covered (passed 2026-11-17) |
| T28 | New task: without assignees; with assignees | `lastChange` is null in both cases (never changed; US15 "Criada") | FR14, US15, openapi `Task.lastChange` | I | `TaskCreateAndReadIT.aNewTaskWithoutAssigneesHasNoLastChange`; `.aNewTaskWithAssigneesHasNoLastChange` | Covered: regression test for finding Q-4, fixed 2026-11-17 |
| T29 | Submit the create form with a title of only spaces (interface) | Error "O título é obrigatório…" shown, form kept, nothing saved (still empty after a reload) | US3 | E2E | `task-create.spec.ts` "a task without a title is not created and the title is flagged as mandatory" | Covered (passed 2026-11-17) |

### 2.4 Malformed requests and failures

| # | Input | Expected | Source | Level | Associated test | Status |
|---|---|---|---|---|---|---|
| T21 | Invalid JSON, non-numeric id (`/tasks/abc`), `projectId` in a PATCH | 400 | BadRequest, D27 | I | `TasksApiIT.anyMemberEditsAndStaleOrEmptyChangesAreHandled` (`projectId`); `AuthFlowIT.malformedRequestsGetTheContractStatusCodes` (invalid JSON, login only) | Partial |
| T22 | Task request with `Content-Type: text/plain` | 415 | openapi.yaml | I | `AuthFlowIT.malformedRequestsGetTheContractStatusCodes` (login only) | Partial |
| T24 | List tasks with `size=101`, `size=0` or `page=-1` | 422 (contract: `page` ≥ 0, `size` 1 to 100) | P7 | I | `ProjectsApiIT.listsOnlyTheUsersProjectsWithSearchAndPagination` (projects, `size=101` only) | Partial |
| T26 | Database error during a task request | 500 generic Problem, no internal details; nothing saved halfway | D28, D29, NFR5 | I | `FailureHandlingIT` (project list and activity log) | Covered (generic handler) |

## 3. Decisions and assumptions

- **[D] 2026-11-17 (David):** Ex. 1 matrix T1 to T26 approved.
- **[D] 2026-11-17 (David):** unit and integration tests run as separate steps (`*Test` / `*IT`); separate plan.
- **[A]** "Access without permission" covers three cases: not a project member (404, E4), project role without permission (403, D23), and no session (401).
- **[A]** The Admin gets 403, not 404, on tasks (implementation-plan.md §4), because the Admin role is known and is never a task reader.
- Existing tests are reused. A new test is written only where a scenario is Partial or Planned and the exercise calls for it.

## 4. Not covered by this plan (limits)

- Performance with 50 users and 1,000 tasks (NFR2), browsers other than Chrome, and security beyond access control (e.g. dependency vulnerabilities) are not proved by these tests. They are recorded as not verified.
- The two Unicode suspicions of Ex. 1 (T25) were confirmed at unit level in Ex. 2: findings Q-1 and Q-2 in [quality-security-review.md](quality-security-review.md). Both were fixed on 2026-11-17 (approved by David). The interface limit (Q-3) stays recorded, not fixed.
- Areas outside tasks and permissions (accounts, password reset, logs, dashboard) are covered by the Worksheet 03 tests listed in [verification.md](verification.md) and are not repeated here.

## 5. Results by exercise

| Exercise | Command | Result (2026-11-17) |
|---|---|---|
| Ex. 2: unit | `cd backend && ./mvnw test -Dtest=TextInputTest` | 9 tests, 7 passed, 2 skipped (`@Disabled`: Q-1, Q-2) |
| Ex. 2: full backend | `cd backend && ./mvnw verify` | 95 tests, 0 failures, 0 errors, 2 skipped; `BUILD SUCCESS` |
| Ex. 2: fix Q-1, Q-2 | `cd backend && ./mvnw test -Dtest=TextInputTest`; `./mvnw verify` | 9 tests, 0 failures; full backend 95 tests, 0 failures, 0 skipped; `BUILD SUCCESS` |
| Ex. 3: integration | `cd backend && ./mvnw verify -DskipUTs -Dit.test=TaskCreateAndReadIT` | 7 tests, 0 failures, 1 skipped (Q-4) |
| Ex. 3: full backend | `cd backend && ./mvnw verify` | Unit 9 tests, 0 failures; integration 93 tests, 0 failures, 1 skipped (Q-4); `BUILD SUCCESS` |
| Ex. 3: fix Q-4 | `cd backend && ./mvnw verify` | Unit 9 tests, 0 failures; integration 93 tests, 0 failures, 0 skipped; `BUILD SUCCESS` |
| Ex. 4: E2E (isolated app: backend :8081 on database `matriz_e2e`, Vite :5174) | `cd frontend && set -a && source ../.env && set +a && MATRIZ_E2E_URL=http://localhost:5174 npx playwright test e2e/task-create.spec.ts` | Run 1: 2 passed; run 2: 2 passed (repeatable; no test project left in the database) |
| Ex. 4: all E2E, same app | `... npx playwright test` | 6 passed, 1 skipped (`admin.spec.ts`, needs `MATRIZ_E2E_ADMIN_PASSWORD`) |
