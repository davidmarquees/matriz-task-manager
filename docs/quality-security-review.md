# Quality and Security Review — Matriz (Worksheet 04)

> One row per finding. **Status:** Confirmed = reproduced by a test or a command · Suspicion = not reproduced yet.
> **Severity:** High = data exposure or a broken permission · Medium = wrong result in a normal flow · Low = edge case with little impact.
> **Decision** is David's; the agent proposes only. Production code is changed only after approval, with a regression test.

## Findings

| # | Finding | File | Evidence | Impact | Severity | Decision | Status |
|---|---|---|---|---|---|---|---|
| Q-1 | A task title made only of non-breaking spaces (U+00A0) is accepted, although A6 says a title cannot be only spaces | `backend/src/main/java/pt/matriz/web/TextInput.java` (`required`: `value.trim()`) | `TextInputTest.refusesATitleMadeOnlyOfNonBreakingSpaces` failed on 2026-11-17: "Expecting code to raise a throwable". `String.trim()` removes only characters up to U+0020. The contract pattern `^\s*\S.{1,148}\S\s*$` would refuse it (in JSON Schema, `\s` includes U+00A0). The database check `char_length(btrim(title))` also trims only ASCII spaces, so it does not catch it either | A task with an invisible title can be saved; the same helper validates project names and comments, so they are likely affected too (not tested) | Low | Fix approved by David (2026-11-17) | Fixed (regression test passes) |
| Q-2 | A task title of 150 characters that contains an emoji is refused as too long | `backend/src/main/java/pt/matriz/web/TextInput.java` (`trimmed.length()`) | `TextInputTest.countsAnEmojiAsOneCharacter` failed on 2026-11-17 with 422 `TOO_LONG`. `String.length()` counts UTF-16 units, so an emoji counts as 2. FR5 says "150 characters"; JSON Schema `maxLength` and PostgreSQL `char_length` both count characters (code points) | A valid title near the limit is refused when it contains emoji or other characters outside the Basic Multilingual Plane; the backend is stricter than the contract and the database | Low | Fix approved by David (2026-11-17) | Fixed (regression test passes) |
| Q-4 | A new task created with assignees already has a `lastChange`, so it shows "Alterada hoje" instead of "Criada" | `backend/src/main/java/pt/matriz/tasks/TaskService.java` (`create` records `ASSIGNEE` / `ADDED` entries; `lastChangesOf` counts every `ASSIGNEE` entry as a change) | First run of `TaskCreateAndReadIT.memberCreatesATaskThatIsReturnedSavedAndReadBack` on 2026-11-17: `lastChange` was not null right after `createTask` with `assigneeIds`. A task created without assignees gets `null` (`aNewTaskWithoutAssigneesHasNoLastChange` passes). The contract (`Task.lastChange`) says "null when the task was never changed", and US15 says a never-changed task shows "Criada" | Wrong "last change" note (and, for Managers, a "who changed it" detail) on every task created with assignees; the creation date is hidden | Medium | Fix approved by David (2026-11-17) | Fixed (regression test passes) |
| Q-5 | When the title is missing, the create form shows an error message, but the title input itself is not marked as invalid (no `aria-invalid`, no link to the message) | `frontend/src/components/TasksSection.tsx` (`CreateTaskForm`: error in `Alert` `task-create-error`) | Code reading during Ex. 4; the E2E test checks the message only. axe (accessibility spec) passed, but it does not check this state | Screen-reader users may not know which field is wrong (US3 asks for the title field to be flagged) | Low | Fix approved by David (2026-11-17) | Fixed (regression test passes) |
| Q-6 | Text with a NUL character (U+0000) gets 500 instead of 422 in every text field (task title, project name, comment, …) | `backend/src/main/java/pt/matriz/web/TextInput.java` (no check for U+0000; PostgreSQL `text`/`varchar` cannot store it) | Ex. 5, on the isolated app (:8081): `POST /api/projects/1/tasks` with title `ab\u0000cd` → 500, with `\u0000abcd` → 500; `POST /api/projects` with name `ab\u0000cd` → 500. Server log: `PSQLException: invalid byte sequence for encoding "UTF8": 0x00`. The answer is the generic Problem (no internal details, NFR5 holds). A NUL in the middle already failed before this worksheet; a leading or trailing NUL was removed by `String.trim()` and became 500 with the Q-1 fix (regression of this worksheet) | Invalid input is reported as a server error instead of a field error (D20, contract 422); error logs filled by any client; no data is exposed or saved | Low | Fix approved by David (2026-11-17), option A: central, 400 | Fixed (regression test passes) |
| Q-7 | Test problem (not an application defect): `ConcurrencyIT.twoManagersDemotingEachOtherAtOnceLeaveOneManager` expected exactly `[200, 409]`, but the second request can also get 403 | `backend/src/test/java/pt/matriz/ConcurrencyIT.java` (assertion); `MemberService.changeRole` checks the Manager role before locking the project | Worksheet 05, Ex. 4: the CI run on the pull request failed with `[200, 403]`; the same commit passed on push and locally. When the first demotion is saved before the second request is checked, its author is no longer a Manager (D10) | Flaky pipeline; the rule E6/D16 held (the request was refused and one Manager remained) | Low | Fix of the test and clarification of D16 approved by David (2026-11-17) | Fixed (test accepts 409 or 403; still checks one Manager left) |
| Q-3 | The task title field in the interface limits the length in UTF-16 units, so a 150-character title with an emoji cannot be typed in full | `frontend/src/components/TasksSection.tsx` (`maxLength={TASK_TITLE_MAX}` on `task-title-input`) | Code reading only: the HTML `maxlength` attribute counts UTF-16 code units. Not reproduced in a browser | Same as Q-2, in the interface only; the API now accepts the title | Low | Recorded in Worksheet 04; fix approved by David (2026-11-17, Worksheet 05) | Fixed (regression tests pass) |

Fix of Q-1 and Q-2 (2026-11-17, approved by David): `TextInput` trims and detects blank text using `Character.isWhitespace` or `Character.isSpaceChar`, so Unicode spaces such as U+00A0 count as spaces, and it counts lengths in code points. The helper is shared, so the same rule now applies to task titles and descriptions, project names and descriptions, comments, and account names and emails created by the Admin. Regression tests: `TextInputTest.refusesATitleMadeOnlyOfNonBreakingSpaces` and `.countsAnEmojiAsOneCharacter`, no longer `@Disabled`. Not changed: the database `CHECK` (`btrim` trims only ASCII spaces), which would need a migration; the backend check enforces the rule (NFR4).

Fix of Q-4 (2026-11-17, approved by David): in `TaskService.lastChangesOf`, an `ASSIGNEE` entry is left out when the same task has a `CREATED` entry with the same timestamp (both are written in the creation transaction, where `now()` does not change). The activity log itself is unchanged (FR13): the entries are still written and shown in the project history. Regression test: `TaskCreateAndReadIT.aNewTaskWithAssigneesHasNoLastChange`, no longer `@Disabled`; `AssigneesAndCommentsApiIT.assigneeChangesCountAsTheLastChange` still passes, so assignees added later still count as a change.

Fix of Q-5 (2026-11-17, approved by David, commit `c3f33c1`): in `CreateTaskForm`, a title error sets `aria-invalid="true"` on the title input and `aria-describedby` to the error message, and moves the focus back to the field. Regression test: `TasksSection.test.tsx` "flags the title field as invalid…" (fails without the fix: "Expected the element to have attribute"). The edit form on the task page was not changed or checked.

Fix of Q-6 (2026-11-17, approved by David, option A): beyond the text fields, the probe also found 500 for a NUL character in the login email, the password reset email and the `q` search of tasks and projects. The fix is central: `ApiExceptionHandler` answers 400 (contract "BadRequest") when the database refuses the input with SQLSTATE 22021 (character not in the UTF-8 repertoire, i.e. U+0000), for any endpoint; other database errors still get the generic 500. The transaction is rolled back, so nothing is saved. This differs from the first proposal (check the input before it reaches the database, in a Jackson module and a filter): the central handler covers JSON bodies, `PatchBody` (read as `JsonNode`, which a Jackson string deserializer would not cover) and query parameters with one change. `openapi.yaml` `BadRequest` description now mentions the NUL character (lint: valid, the same 2 warnings as before). Regression test: `NulCharacterIT` (3 tests: task and project text, PATCH, search, login and reset email; 3 failures with 500 without the fix, 3 passed with it).

## Ex. 5: Code Review (diff `v1.0.0..HEAD`, 2026-11-17)

Scope: production changes of this worksheet (`TextInput`, `TaskService.lastChangesOf`, `backend/pom.xml`) and the new tests. Focus: bugs, validation, error handling, missing tests. Style-only remarks left out.

| Item | Observation | Type | Outcome |
|---|---|---|---|
| `TextInput.trim` | Removes Unicode spaces as intended, but control characters below U+0020 that are not whitespace (U+0000 to U+0008, U+000E to U+001B) are no longer removed at the edges | Fact (code + probe) | U+0000 → finding Q-6. Other control characters (e.g. U+0007) are accepted and stored: the requirements say nothing about them, so this is not a finding. **Decided** (David, 2026-11-17, Worksheet 05): rule D49 in [database.md](database.md), 422 `INVALID_CHARACTER`; see "Worksheet 05: Q-3 and control characters" below |
| `lastChangesOf` (Q-4 fix) | Relies on the creation entries sharing one transaction timestamp (`now()`) | Hypothesis | Holds today (one `@Transactional` method); would break if creation were split into several transactions. Covered by the regression test `TaskCreateAndReadIT.aNewTaskWithAssigneesHasNoLastChange`. No action |
| `backend/pom.xml` | Surefire `<skip>${skipUTs}</skip>`, Failsafe `integration-test` + `verify`; `-DskipTests` skips both | Fact | No problem |
| Tests | No test of the Unicode rules (Q-1, Q-2) or of NUL through the API, or for project names, comments and account fields | Fact | Missing tests; one would be added with the Q-6 fix |
| Frontend | `CreateTaskForm` checks the minimum length with `String.length` (UTF-16), so "a😀" passes the form and the API answers 422 `TOO_SHORT` (shown as an error) | Fact (code reading) | Same cause as Q-3; recorded there |

## Worksheet 05: Q-3 and control characters (2026-11-17)

Both fixes approved by David before Worksheet 05, Ex. 2, on branch `chore/delivery`.

- **Q-3 (frontend):** the task title fields (create form and edit form) no longer use the HTML `maxlength` attribute, which counts UTF-16 units. A new helper `frontend/src/text.ts` (`charCount`, `limitChars`) counts and cuts by code points, as the API does; the create form also checks the minimum with it. Regression tests in `TasksSection.test.tsx`: both failed without the fix (`maxlength` still present; "a😀" sent to the API) and pass with it. Other fields with `maxLength` (descriptions, comments, project names, search) keep the attribute: their limits are long, and the same UTF-16 cut can only happen near the limit with emoji. Not changed (outside Q-3).
- **Control characters (rule D49):** `TextInput` refuses U+0000 to U+001F and U+007F with 422 `INVALID_CHARACTER`. Single-line text (task titles, project names, account names and emails created by the Admin) accepts none; multi-line text (task and project descriptions, comments: new `TextInput.requiredMultiLine`) accepts tab, line feed and carriage return. Edges are trimmed first, as before (A6). New code in `openapi.yaml` (`INVALID_CHARACTER`) and one message in the interface (pt-PT and en) for every form that sends these fields.
- **Effect on Q-6:** a NUL character in those text fields is now 422 `INVALID_CHARACTER` (refused before it reaches the database), not 400. A NUL in other inputs (search `q`, login and reset email) still gets 400 from the central handler. `NulCharacterIT` was updated accordingly and has a fourth test for the other control characters.
- Not changed: the database `CHECK` constraints (the backend enforces the rule, NFR4); passwords and search terms (not text that is stored or shown).

## Worksheet 05, Ex. 4: Semgrep and image scan (2026-11-17)

Both run in the CI pipeline (`.github/workflows/ci.yml`) from their official Docker images, pinned: `semgrep/semgrep:1.185.0` and `aquasec/trivy:0.76.2`. First runs on the developer's machine:

- **Semgrep** (`p/java`, `p/typescript`, `p/react`): 94 files scanned, 0 findings. The pipeline fails on any finding (`--error`).
- **Trivy** (HIGH and CRITICAL with a published fix, on the Compose images), before the fixes:
  - Frontend (nginx-unprivileged, Alpine 3.24.2): 13 in Alpine packages (brotli, curl, libexpat, libxslt, musl, zlib). Fix: `apk upgrade --no-cache` in `frontend/Dockerfile`. After: 0.
  - Backend: 14 in Java libraries. `tomcat-embed-core` 10.1.55 (5 CRITICAL, 1 HIGH), `jackson-core`/`jackson-databind` 2.21.4 (5 HIGH), `postgresql` 42.7.11 (1 HIGH): fixed by overriding the Spring Boot managed versions in `backend/pom.xml` (`tomcat.version` 10.1.62, `jackson-bom.version` 2.21.8, `postgresql.version` 42.7.12); all backend tests pass (unit 13, integration 97). After: 2.
  - **Accepted for now** (David, 2026-11-17): CVE-2026-47884 and CVE-2026-47890 (CRITICAL) in `spring-webmvc` 6.2.19. It is the last Spring Framework 6.2 release, and Spring Boot 3.5.16 the last 3.5 release; the fix exists only in Spring Framework 7.0.9 (Spring Boot 4), a migration outside this worksheet. Listed in `.trivyignore.yaml` with an expiry date (2027-02-17), after which the pipeline fails again. **Impact on Matriz not analysed.**
- The image scan also covers the backend's Java dependencies (inside `app.jar`), which had no audit before (Worksheet 04, "Backend dependencies: not checked").

## Ex. 5: Security Review, access to projects and tasks (2026-11-17)

**Code reading.** Every task, assignee and comment operation first loads the task's project and calls `requireProjectMember` (404 for non-members, 403 for the Admin); every project change calls `ProjectAccess.requireManager` (404 non-member, 403 Member or Admin); the activity log calls `requireManager`. Identity (`createdBy`, comment author) comes from the session; unknown fields are refused (400). Membership is read from the database on each request (not cached in the session).

**Manual IDOR check with the two demo accounts** on the isolated app (backend :8081, database `matriz_e2e`), script with `curl`: the demo Manager creates a task in "Annual report" (Member not in it) and one in "Website redesign" (Member is a Member).

| Attempt by Membro Demo | Expected | Observed |
|---|---|---|
| "Annual report": GET project, members, tasks, task, comments, activity; POST task, assignee, comment; PATCH and DELETE task and project; add self as member or Manager (15 requests) | 404 each, no content | 404 each; no response contained the task title or the project name |
| Project search `?q=Annual`, dashboard | 200 without "Annual report" | 200, not listed |
| "Website redesign": delete the Manager's task; edit or delete the project; promote self; appoint self Manager; remove the Manager; read the activity log (7 requests) | 403 each | 403 each |
| Data afterwards (Manager view) | Unchanged | Task title, version 0, 0 assignees, 0 comments, 1 member in "Annual report": unchanged |

Result: **24 of 24 checks as expected; no IDOR or broken access control confirmed.** The same rules are covered by automated tests (`TaskCreateAndReadIT`, `TasksApiIT`, `ProjectsApiIT`, `AssigneesAndCommentsApiIT`, `ProjectAccessIT`).

**Secrets.** Tracked files contain no real secrets: `.env` is git-ignored; `.env.example` and the tests use fictitious values only. The password reset log line has the user id only, not the link.

## Ex. 5: Static analysis (2026-11-17)

No static analysis tool is configured in the project (no ESLint, Semgrep, SpotBugs or SonarQube). Decision (David, 2026-11-17): use what is available now; add Semgrep to the CI pipeline in Worksheet 05.

| Tool | Command | Result |
|---|---|---|
| Dependency audit (frontend) | `cd frontend && npm audit` | 0 vulnerabilities (also with `--omit=dev`) |
| TypeScript type check + build | `cd frontend && npm run build` | OK |
| Frontend tests | `cd frontend && npm test` | 7 files, 19 tests passed |
| Backend dependencies | — | Not checked: no tool configured (e.g. OWASP Dependency-Check); Worksheet 05 |

## Checks run

| Date | Command | Result |
|---|---|---|
| 2026-11-17 | `cd backend && ./mvnw test -Dtest=TextInputTest` (before `@Disabled`) | 9 tests: 7 passed, 1 failure (Q-1), 1 error (Q-2) |
| 2026-11-17 | `cd backend && ./mvnw test -Dtest=TextInputTest` | 9 tests, 0 failures, 2 skipped (Q-1, Q-2) |
| 2026-11-17 | `cd backend && ./mvnw verify` | 95 tests, 0 failures, 0 errors, 2 skipped; `BUILD SUCCESS` |
| 2026-11-17 | After the fix: `cd backend && ./mvnw test -Dtest=TextInputTest` | 9 tests, 0 failures, 0 skipped |
| 2026-11-17 | After the fix: `cd backend && ./mvnw verify` | 95 tests, 0 failures, 0 errors, 0 skipped; `BUILD SUCCESS` |

| 2026-11-17 | `cd backend && ./mvnw verify -DskipUTs -Dit.test=TaskCreateAndReadIT` (first run) | 5 tests, 2 failures: Q-4, and a test problem (the two 404 bodies differ only in `instance`, which echoes the path; the test now compares them without it) |
| 2026-11-17 | `cd backend && ./mvnw verify -DskipUTs -Dit.test=TaskCreateAndReadIT` | 7 tests, 0 failures, 1 skipped (Q-4) |
| 2026-11-17 | `cd backend && ./mvnw verify` | Unit 9 tests, 0 failures; integration 93 tests, 0 failures, 1 skipped (Q-4); `BUILD SUCCESS` |
| 2026-11-17 | After the Q-4 fix: `./mvnw verify -DskipUTs -Dit.test='TaskCreateAndReadIT,TasksApiIT,AssigneesAndCommentsApiIT,ActivityApiIT'` | 24 tests, 0 failures, 0 skipped |
| 2026-11-17 | After the Q-4 fix: `cd backend && ./mvnw verify` | Unit 9 tests, 0 failures; integration 93 tests, 0 failures, 0 skipped; `BUILD SUCCESS` |
| 2026-11-17 | Ex. 4: `npx playwright test e2e/task-create.spec.ts` (isolated app on :5174, database `matriz_e2e`), twice | 2 passed each time; "Criada hoje" shown on a new task. The create form has no assignee field, so Q-4 (assignees at creation) is not exercised in the interface |
| 2026-11-17 | Ex. 4: `npx playwright test` (all specs, same app) | 6 passed, 1 skipped (Admin spec) |
| 2026-11-17 | Ex. 5: IDOR script with the two demo accounts (isolated app) | 24/24 as expected |
| 2026-11-17 | Ex. 5: NUL probe on the isolated app | 500 for U+0000 in task title and project name (Q-6); U+0007 accepted (201) |
| 2026-11-17 | Q-5: `cd frontend && npm test`; `npm run build`; E2E (isolated app) | 20 tests passed; build OK; E2E 6 passed, 1 skipped |
| 2026-11-17 | Q-6, without the fix: `./mvnw verify -DskipUTs -Dit.test=NulCharacterIT` | 3 tests, 3 failures (`Status expected:<400> but was:<500>`) |
| 2026-11-17 | Q-6, with the fix: `cd backend && ./mvnw verify` | Unit 9 tests, 0 failures; integration 96 tests, 0 failures, 0 skipped; `BUILD SUCCESS` |
| 2026-11-17 | Contract: `npx --yes @redocly/cli@latest lint openapi.yaml` | Valid; 2 warnings, the same as before the change |
| 2026-11-17 | Worksheet 05, Q-3, without the fix: `npx vitest run src/test/TasksSection.test.tsx` | 5 tests, 2 failures (the two Q-3 tests) |
| 2026-11-17 | Worksheet 05, Q-3 and D49: `cd frontend && npm test && npm run build` | 23 tests passed; build OK |
| 2026-11-17 | Worksheet 05, D49: `cd backend && ./mvnw verify` | Unit 13 tests, 0 failures; integration 97 tests, 0 failures, 0 skipped; `BUILD SUCCESS` |
| 2026-11-17 | Worksheet 05: contract lint after `INVALID_CHARACTER` | Valid; 2 warnings, the same as before |

## Not checked

- Q-1 and Q-2 through the API (only the unit level and the full suite were run). In the interface, JavaScript `trim()` already removes U+00A0, so Q-1 could only be reached through the API (code reading).
- Q-3 and D49 in a browser (component and integration tests only; E2E not run for these fixes).
- Q-4 in the interface (expected: "Alterada hoje" on a new task created with assignees), and whether adding an assignee in the same request as an update is affected.
- Project names, comments and account fields with Unicode spaces or emoji: they use the same helper, but no test of their own.
- Security beyond access to projects and tasks (session handling, rate limit, password reset) was reviewed in Worksheet 03, not again here.
- No SAST on the Java code (Semgrep planned for Worksheet 05). Done in Worksheet 05, Ex. 4 (see above).
- Q-6 fix on the running isolated app (checked with the integration test only).
