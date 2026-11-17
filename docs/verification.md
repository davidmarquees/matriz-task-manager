# Verification — Matriz

Verified scenarios, one row each: what was expected, what was observed and the evidence.
Type: **Auto** = automated test · **Manual** = command or check run by hand.
Worksheet 03: Ex. 8 scenarios (01 to 09) in increment 7; Ex. 9 review, refactoring and examples of AI use in increment 9.

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

## Increment 3 — Projects and members (Ex. 4), 2026-11-16

| # | Scenario | Type | Expected | Observed | Evidence |
|---|---|---|---|---|---|
| V-15 | Backend build and tests | Auto | All tests pass | 45 tests, 0 failures, `BUILD SUCCESS` | `cd backend && ./mvnw verify` (new: `ProjectsApiTest` 11; `SessionCookiesTest` extended) |
| V-16 | Create, then read back (persistence) | Auto + Manual | A Manager creates a project; it is saved with the creator from the session, the creator is its Manager, an activity entry is written; it appears in the list without reloading, and after reloading the page and restarting the backend | API: 201, `Location`, row in `projects` with `created_by` = the session user, `project_members` = MANAGER, `activity_log` CREATED. Browser: "Lançamento da app móvel" listed at once ("Projeto … criado."), still listed after reload and after restarting the backend and logging in again (sessions are in memory) | `ProjectsApiTest.managerCreatesAProjectThatIsSavedAndListed`; built-in browser, demo Manager |
| V-17 | Validation (D20) | Auto + Manual | Name only of spaces → 422 `BLANK`; over 100 → `TOO_LONG`; missing → `REQUIRED`; description over 2,000 → 422; number as name → 422 `INVALID_VALUE`; unknown field (`createdBy`) → 400; nothing saved | As expected; in the browser "O nome é obrigatório." and nothing created | `ProjectsApiTest.refusesInvalidNamesAndDescriptions`; browser |
| V-18 | Authorisation with different users (D9 to D11, D17, D18) | Auto + Manual | Member and Admin cannot create (403); non-member gets 404 on read, members, edit and delete, also by changing the id in the URL; Member gets 403 on edit and delete; Admin sees all projects without counts and gets 403 on changes; removed member gets 404 | As expected. Browser, demo Member: only "Website redesign" listed, no create form, `/projects/2` ("Annual report") shows "Projeto não encontrado." | `ProjectsApiTest` (5 tests); browser |
| V-19 | Search, pagination, edit, delete (FR10, D25, D43, A11) | Auto + Manual | Search by name ignores case and treats `%` as text; pages with `totalItems` over all matches; edit with stale version → 409 `VERSION_CONFLICT`, no real change → same version; delete removes members, tasks, assignees and comments and keeps the activity entry | As expected. Browser: search "APP" → 1 project, "xyz" → "Nenhum projeto encontrado."; rename saved ("Alterações guardadas."); delete with confirmation → back to the list, `/projects/3` → "Projeto não encontrado." | `ProjectsApiTest`; browser (confirmation dialog accepted automatically in the test browser) |
| V-20 | Members (FR3, D13 to D16, E1, E6) | Auto + Manual | Add by email (any case); again → 409 `ALREADY_MEMBER`; deactivated, Admin or unknown email → 422 `USER_NOT_ELIGIBLE`; Member cannot add (403); last Manager cannot leave or be demoted (409 `LAST_MANAGER`); removing a member removes their assignments and keeps the tasks; role changes written to the user audit log | As expected. Browser: removing the only Manager → "O projeto tem de ter pelo menos um Gestor."; adding the demo Member → listed | `ProjectsApiTest` (3 tests); browser |

### Bug found during this increment

| Step | Detail |
|---|---|
| Reproduction | Log in, click "Terminar sessão", log in again on the same page without reloading. |
| Expected / observed | Expected: logged in. Observed: the first attempt failed ("Não foi possível iniciar sessão"); the second worked. |
| Cause | Logout cleared the `XSRF-TOKEN` cookie and no new token was sent, so the next login had no CSRF token (403). |
| Fix | `AuthController.logout` now sends a new CSRF token in the logout answer. |
| Regression test | `SessionCookiesTest`: logs in again with the token from the logout answer → 200. Browser check after the fix: logout, then login at once → works. |

Not checked in this increment: two Managers demoting each other at the same instant (the row lock is in place but there is no concurrent test); the layout at 360 px (Ex. 7).

## Increment 4 — Tasks (Ex. 5) and the "last change" extra, 2026-11-16

| # | Scenario | Type | Expected | Observed | Evidence |
|---|---|---|---|---|---|
| V-21 | Backend build and tests | Auto | All tests pass | 54 tests, 0 failures, `BUILD SUCCESS` | `cd backend && ./mvnw verify` (new: `TasksApiTest` 9) |
| V-22 | Create and read back; persistence (US3, A5) | Auto + Manual | A member creates a task with defaults TODO / MEDIUM, the creator from the session; it is listed at once and survives a backend restart | API as expected. Browser: "Rever os textos legais" created ("Tarefa … criada."), "A fazer" went from 2 to 3 without reloading; after restarting the backend (and logging in again) the task and its changes were still there | `TasksApiTest.memberCreatesATaskWithDefaultsThatIsSavedAndReadBack`; browser |
| V-23 | Validation (D20, E5, E7) | Auto + Manual | Title only of spaces → `BLANK`; under 3 → `TOO_SHORT`; over 150 → `TOO_LONG`; missing → `REQUIRED`; description over 2,000 → 422; invalid status / priority → `INVALID_VALUE`; invalid date → `INVALID_DATE`; duplicate or non-member assignee → 422; unknown field → 400; nothing saved | As expected; browser shows "O título é obrigatório e não pode ter só espaços." and keeps the form | `TasksApiTest.refusesInvalidTaskData`, `assigneesMustBeActiveProjectMembers`; browser |
| V-24 | Status, priority, filters and counts (US5, US6, E2, E3) | Auto + Manual | Any member edits (A13); stale version → 409; no real change → same version (D43); `projectId` in PATCH → 400 (D27); text search in title and description; filters combined with AND; assignee filter; empty result → "Nenhuma tarefa encontrada."; counts and dashboard follow every change | As expected. Browser: changing the status in the row moved the counts (A fazer 3 → 2, Em curso 1 → 2) without reloading; filter Em curso + Alta → 2 tasks | `TasksApiTest` (3 tests); browser |
| V-25 | Delete and access (A10, D11, D17, D23, D26) | Auto + Manual | Member cannot delete a task they did not create (403); creator and Manager can; outsider gets 404 on every task operation, also by changing ids; Admin gets 403; deleted task → 404 and its title stays in the activity log | As expected. Browser: Manager deleted the task with confirmation → back to the project, counts updated ("4 tarefas"), `/tasks/7` → "Tarefa não encontrada." | `TasksApiTest` (2 tests); browser (confirmation accepted automatically in the test browser) |
| V-26 | Extra: "last change" note, option A | Auto + Manual | Every member sees the date; only Managers see who and what; description values not repeated | API as expected. Browser: the Member saw "Alterada hoje" as plain text; the Manager, on hover, saw "Membro Demo · 16/11/2026 15:08 · Estado: A fazer → Em curso" | `TasksApiTest.lastChangeShowsWhoAndWhatOnlyToManagers`; browser screenshot check |

Not checked in this increment: the delete button hidden for a Member on someone else's task (checked only in the API, 403); the note on a touch screen; the layout at 360 px (Ex. 7).

## Increment 5 — Assignees and comments (Ex. 6), 2026-11-16

Browser checks ran on ports 8081 (backend) and 5174 (frontend), so the developer's servers on 8080 and 5173 were not touched. The development database was stopped and was started again with `docker compose up -d`.

| # | Scenario | Type | Expected | Observed | Evidence |
|---|---|---|---|---|---|
| V-27 | Backend build and tests | Auto | All tests pass | 60 tests, 0 failures, `BUILD SUCCESS` | `cd backend && ./mvnw verify` (new: `AssigneesAndCommentsApiTest` 6) |
| V-28 | Several assignees, no duplicates (US4, D21, D22) | Auto + Manual | Several eligible users per task; the same user twice → 409 `ALREADY_ASSIGNED`; a non-member or unknown user → 422 `NOT_A_PROJECT_MEMBER`; each assignment removable; removing one that does not exist → 404; still there when the task is reopened | As expected. Browser (demo Member): assigned "Gestor Demo" → "Responsáveis (2)", the picker then said "Todos os membros do projeto já estão atribuídos."; after reloading, both still listed; removing "Gestor Demo" → "Responsáveis (1)" | `AssigneesAndCommentsApiTest` (2 tests); browser |
| V-29 | Comments with author and date (US7, FR11, A14) | Auto + Manual | Author and date set by the server (an `authorId` in the body → 400); oldest first; text trimmed; empty, only spaces or null → 422; over 2,000 → `TOO_LONG`; the text is not copied to the activity log; still there after reloading | As expected. Browser: only spaces → "O comentário não pode estar vazio nem ter só espaços."; "Proponho tons de azul e cinzento." shown as "Membro Demo · 16/11/2026 15:58" and still there after reloading | `AssigneesAndCommentsApiTest` (2 tests); browser |
| V-30 | Access (E4, D11, D17) | Auto + Manual | Outsider → 404 on assignees and comments; Admin → 403; Member does not see "Eliminar tarefa" on a task created by the Manager | As expected | `AssigneesAndCommentsApiTest`; browser (closes the gap left in V-26) |
| V-31 | FR14 counts assignee changes | Auto + Manual | After an assignment the task shows "Alterada hoje"; Managers see "Responsáveis alterados" by the member; a comment does not change it | As expected; browser showed "Alterada hoje" after the assignment | `AssigneesAndCommentsApiTest.assigneeChangesCountAsTheLastChange`; browser |

Test data left in the development database: one comment on the demo task "Define the new colour palette" (comments cannot be deleted, A14). The assignment made for the test was removed.

Not checked in this increment: two users assigning the same person at the same instant (guaranteed by the primary key, no concurrent test); the layout at 360 px (Ex. 7).

## Increment 6 — Complete frontend (Ex. 7), 2026-11-16

Browser checks on ports 8081 (backend) and 5174 (frontend), with the demo accounts. Screenshots were shared with the developer in the project thread.

| # | Scenario | Type | Expected | Observed | Evidence |
|---|---|---|---|---|---|
| V-32 | Builds and tests | Auto | Backend and frontend tests pass; build passes | Backend 61 tests (new: preferences), frontend 11 tests (Vitest), `npm run build` OK, `npm audit` 0 vulnerabilities | `./mvnw verify`, `npm test`, `npm run build` |
| V-33 | Preferences saved in the profile (NFR1, NFR8, US13) | Auto + Manual | Language and theme saved and returned on another login; invalid value → 422; audited | As expected. Browser: switching to EN translated the whole dashboard ("Dashboard", "1 of 6 tasks done"); dark theme applied at once; `GET /api/auth/me` returned the saved values; restored to pt-PT / SYSTEM afterwards | `AuthFlowTest.preferencesAreSavedInTheProfileAndAudited`; browser |
| V-34 | Login before authentication (A24) | Manual | Browser language decides before login (English browser → English page); PT / EN switch works before login | Login page shown in English, switched to PT, then logged in | Browser screenshot 1-login |
| V-35 | Dashboard (FR9, US5, E2) | Manual | Totals, projects with counts and progress bars, overall progress, shortcuts; light and dark | 2 projects, 3 / 2 / 1 tasks, "17 %" (1 of 6 done), as in the approved design | Screenshots 2-painel-claro, 3-painel-escuro |
| V-36 | Mobile 360 px (NFR7, US14) | Manual | No horizontal scrolling; menu ☰ with navigation, preferences and log out | `scrollWidth` = `clientWidth` = 360; one KPI ↗ button overflowed its card and was fixed (smaller cards on narrow screens); menu opens and closes | Screenshot 4-telemovel-360; `document.documentElement.scrollWidth` |
| V-37 | Full path in the new interface | Manual | Create project → listed at once with a toast; create task with Enter → counts updated; change status → counts updated; delete project with the custom dialog | As expected; the dialog opened with the focus on "Cancelar"; the test project was deleted at the end | Screenshots 5-projeto, 6-tarefa, 7-dialogo-eliminar |
| V-38 | Translations and accessible components | Auto | Same keys in PT and EN; status shown as words; counts with text labels (never colour alone); dialog starts on the safe choice, Escape cancels; login errors generic and form data kept; same-password refused before calling the API | As expected | `components.test.tsx`, `LoginPage.test.tsx`, `ChangePasswordPage.test.tsx` |

Not checked in this increment: an automated contrast audit (contrast ratios were calculated by hand for the design tokens); screen-reader testing with a real screen reader; every screen at 360 px (dashboard, menu and login checked).

### Correction after the developer's test: one style for every form control (2026-11-16)

| # | Scenario | Type | Expected | Observed | Evidence |
|---|---|---|---|---|---|
| V-39 | Search, dropdowns and inputs share height and radius | Manual | Every search field, input, date and dropdown has the same height, radius, border and arrow, in every page and both themes | Before: search and form fields 46 px with 14 px corners; dropdowns in task rows and member roles 40 px pill-shaped with the browser's own arrow. After: every control 46 px / 14 px (measured on the project page: 15 controls, task page: 6, projects page: 3), same border and a drawn arrow in light and dark; textareas keep 14 px corners with a larger height | `getBoundingClientRect` and `getComputedStyle` in the browser; screenshots before / after |


## Increment 7 — Ex. 8 · scenarios 01–09, 2026-11-16

Fresh run of every check on the code of this increment. Manual checks in the browser on ports 8081 (backend, started from the built jar) and 5174 (Vite), with the demo accounts Gestor Demo (Manager) and Membro Demo (Member) and the development database; API checks with `curl` and a cookie jar per user. The test project "Verificação Ex8" was created for the run and deleted in scenario 09. "Auto" = automated test, "Manual" = done by hand in the browser or with `curl`.

### Commands and overall results

| Command | Result |
|---|---|
| `cd backend && ./mvnw -B verify` | 81 tests, 0 failures, 0 errors, BUILD SUCCESS (the logged "Password reset email could not be sent" is the expected output of the D36 test) |
| `cd frontend && npm test` | 19 tests passed (7 files), including the regression test of the bug below |
| `cd frontend && npm run build` | OK |
| `npx @redocly/cli lint openapi.yaml` (0.7.0) | Valid; the same 2 accepted warnings |
| `cd frontend && npm run e2e` (Playwright 1.64.1, Google Chrome, against 5174 and a database for tests only, `matriz_verify`) | 5 passed, after the bug fix and without the earlier workaround |

### Required automated tests (Worksheet: validation, authorisation, create then read)

| Rule | Test | Result |
|---|---|---|
| Validation rule | `TasksApiTest.refusesInvalidTaskData` (blank title, invalid status, priority and date → 422, nothing saved) | Passed |
| Authorisation | `ProjectsApiTest.nonMembersCannotSeeAProjectEvenByChangingTheId` (a non-member gets 404 for project, members and tasks of another project) | Passed |
| Create and read back from the database | `ProjectsApiTest.managerCreatesAProjectThatIsSavedAndListed` (201, then the row is read from PostgreSQL and listed) | Passed |

### Scenarios

| # | Scenario | Type | Expected | Observed | Evidence |
|---|---|---|---|---|---|
| 01 | Access to the application | Manual + Auto | Valid credentials log in; invalid ones get a generic error with the form kept; requests without a session are refused | Wrong password: "Email ou palavra-passe inválidos." (in EN "Invalid email or password."), email kept in the field. Correct password: logged in and sent back to `/projects`, the page asked before login. `GET /api/projects` without session → 401; `POST /api/auth/login` without CSRF token → 403 | Browser; `curl`; `AuthFlowTest`, `SessionTimeoutTest` (session expires after inactivity → 401) |
| 02 | Project management | Manual + Auto | Create, search, edit and delete work according to permissions | Created "Verificação Ex8" (toast, listed at once); search "verifica" found it (1 project); renamed to "Verificação Ex8 (editado)" (toast "Changes saved."); deleted in scenario 09. Member: `POST /projects` → 403, `PATCH` and `DELETE /projects/20` → 403 (member, not Manager) | Browser; `curl`; `ProjectsApiTest` |
| 03 | Task persistence | Manual | The task appears without reloading and is kept after restarting the backend | "Tarefa Ex8" (priority High) appeared in the list and the counts changed to "1 to do" without reloading. After stopping and starting the backend (22:51), the session had ended (sessions are in memory, as documented); after logging in again the page showed "Tarefa Ex8 / High" | Browser; backend log "Started MatrizApplication" |
| 04 | Data validation | Manual + Auto | Empty or spaces-only required fields and invalid values are refused | Interface: project name with spaces only → "The name is required.", description kept; task title with spaces → "The title is required and cannot be only spaces."; comment with spaces → "The comment cannot be empty or only spaces.". API: name `"   "` → 422 BLANK; name of 101 characters → 422 TOO_LONG; task title `"  "` → 422 BLANK; priority `URGENT`, status `LATER` → 422 INVALID_VALUE; date `2026-02-30` → 422 INVALID_DATE; title as a number → 422 INVALID_VALUE; comment `"   "` → 422 BLANK. No task was created by these requests | Browser; `curl`; `TasksApiTest.refusesInvalidTaskData`, `ProjectsApiTest` |
| 05 | Status and priority | Manual + Auto | Editing updates the task, the filters and the counts consistently | Status To do → In progress in the row: counts "0 to do 1 in progress 0 done" at once; filter To do → 0 tasks, In progress → 1. Priority High → Low: filter High → 0, Low → 1 | Browser; `TasksApiTest` (counts and dashboard) |
| 06 | Assignees | Manual + Auto | Add and remove work; the same assignment is never duplicated | Added Membro Demo (list shows "Membro Demo"; the select no longer offers them); removed with the dialog (0 assignees); added again. Duplicate request → 409 ALREADY_ASSIGNED; two identical requests at the same moment → one 201 and one 409, one row (5 rounds) | Browser; `AssigneesAndCommentsApiTest`, `ConcurrencyTest` (D21) |
| 07 | Comments | Manual + Auto | Content, authenticated author and date are saved and shown | "Comentário de verificação Ex8" shown with "Gestor Demo · 16/11/2026 22:52"; `GET /tasks/21/comments` returned the same body, author id 1 and `createdAt`. The author comes from the session, not from the request | Browser; `curl`; `AssigneesAndCommentsApiTest` |
| 08 | Authorisation in the API | Manual + Auto | Another user cannot read or change resources without permission, even by changing ids | Member (not in "Annual report", id 2): `GET /projects/2` → 404, `GET /tasks/6` → 404, `PATCH /tasks/6` → 404, `POST /tasks/6/comments` → 404, `GET /projects/2/activity` → 404, `GET /projects/99999` → 404 (same answer as a missing project, so ids reveal nothing). As member of project 20: `PATCH`/`DELETE /projects/20` → 403, `GET /projects/20/activity` → 403. `GET /admin/users` → 403. Afterwards the task title ("Review the draft") and the project name were unchanged. Comments have no edit or delete operation in the contract (route → 404) | `curl` with the member's session; `ProjectsApiTest.nonMembersCannotSeeAProjectEvenByChangingTheId`, `TasksApiTest`, `ActivityApiTest`, `AdminApiTest.onlyTheAdminManagesAccounts` |
| 09 | Deletion and errors | Manual + Auto | Deletion respects dependencies; API errors never show success nor lose form data | Conflict: with the edit form open, the project was changed in another session (`PATCH` 200); saving the form showed "Someone else changed the project. Reload the data and try again." with a Reload button, the typed name kept, no toast. Deletion: dialog "The project … its tasks and comments will be deleted. This cannot be undone." opened with the focus on Cancel; after confirming, "Project deleted." and back to the list; `GET /projects/20`, `/tasks/21`, `/tasks/21/comments` → 404; in the database 0 tasks, comments, assignees and members of the project; the 13 activity log rows are kept (append-only). Server error: generic 500 without internal details, nothing saved | Browser; `curl`; `psql`; `FailureHandlingTest`, `ProjectsApiTest`, `TasksApiTest` |

### Extra checks of this increment (approved improvements)

| # | Scenario | Type | Expected | Observed | Evidence |
|---|---|---|---|---|---|
| V-40 | Concurrent changes (D16, D21) | Auto | Two Managers demoting each other at once leave one Manager; the same assignee added twice at once gives one row | 5 rounds each: statuses {200, 409} and {201, 409}; always 1 Manager and 1 row | `ConcurrencyTest` |
| V-41 | Session expiry (A18) and server errors (D28, D29) | Auto | Session timeout 30 min; an expired session gets 401; a database failure gives a generic 500 and saves nothing | As expected | `SessionTimeoutTest`, `FailureHandlingTest` |
| V-42 | Filter by assignee (US6) and dashboard ↗ by status | Auto + Manual | Assignee filter with "Eu"; the ↗ of a total opens the projects with that status filter and keeps it on the project page | As expected; E2E clicked the "Done" ↗ and saw the filter hint | `TasksSection.test.tsx`; `main-flow.spec.ts` |
| V-43 | Project history (FR13, US12) | Auto + Manual | Managers read who changed what and when; Members get 403, non-members 404 | As expected; the project showed 13 entries before deletion | `ActivityApiTest`; `main-flow.spec.ts`; browser |
| V-44 | Password reset by email (US11, D30–D36) | Auto + Manual | Same answer for any email; link by email, single use, 30 min; sessions end; mail server failure gives the same answer | Browser on 5174 with Mailpit: "Se o email pertencer a uma conta ativa, enviámos um link…"; Mailpit received "Matriz · Redefinir a palavra-passe" for member@example.test with a link to `http://localhost:5174/reset-password?token=…` valid for 30 minutes; the new password was saved ("Palavra-passe alterada…"; the demo password was set again, so the account is unchanged); the same link again → "O link é inválido, expirou ou já foi usado." with "Pedir novo link"; login with the password → 200 | `PasswordResetTest` (5); `PasswordReset.test.tsx`; browser; Mailpit API |
| V-45 | Account administration (US9, US10, US12, D1–D5, D12, D14, D19, D44) | Auto + Manual | Admin only; create (email unique, must change password); deactivate (sessions end, memberships and assignments removed, tasks kept); refused for own account and for the only Manager until another is appointed; reactivate with a new initial password and no projects; anonymise only after deactivation, final; audit log without personal values | As expected. Browser on a database for tests only (`matriz_verify`, approved by David, deleted at the end): first Admin login → "Tem de alterar a palavra-passe antes de continuar.", then the "Contas" menu item; own account has no "Desativar"; deactivating Gestor Demo → "Esta pessoa é a única gestora de um projeto…", no toast; as Admin the project page showed "Como Administrador vê os dados e os membros do projeto, mas não as tarefas.", no tasks and no edit button; appointing the Admin → "Esse email não pertence a uma conta ativa…"; appointing Membro Demo → "Gestor nomeado." in both projects (added to the second); then Gestor Demo deactivated with the dialog; reactivation with a 5-character password → refused, with 8+ → "Conta reativada…"; deactivated again and anonymised → "Utilizador removido", `removed-1@invalid`, only "Auditoria" left; the audit of that account listed all 10 actions with "Administrador" or "Sistema" as actor; 360 px: no horizontal scrolling on the Admin, forgot-password and reset pages | `AdminApiTest` (7); `AdminPage.test.tsx` (2); `admin.spec.ts`; browser |
| V-46 | Automatic accessibility audit (NFR6) | Auto | No serious or critical axe-core violation (WCAG 2.1 A/AA rules) on login, password reset, dashboard, projects, project and task pages, light and dark | 0 serious or critical violations in both themes | `accessibility.spec.ts` (report in `frontend/playwright-report`) |

### Bug found during the E2E run: next login opened the previous user's page (fixed)

- **Steps:** log in as Membro Demo, open a project page, log out with the menu button; on the same browser tab log in as Gestor Demo.
- **Expected:** after an explicit logout, the next login opens the dashboard.
- **Observed:** the next login opened the project page the previous user had open (`/projects/{id}`). The server still checks access, so no data leaked.
- **Evidence:** Playwright snapshot of the failing first E2E run (Manager on the project page instead of the dashboard); in the browser, after logout `history.state.usr` was `{"from":"/projects/1"}`.
- **Cause:** `AuthContext.logout()` sets the state to anonymous; `RequireAuth` then redirects to `/login` keeping `state.from` = the current page; `LoginPage` sends the next user there. The shell's own `navigate('/login')` only sometimes wins the race (it did in jsdom, not in the browser).
- **Fix (approved by David, limited to two files):** an explicit logout sets `loggedOut: true` in the anonymous state, and `RequireAuth` redirects without `from` in that case. An expired session or a first visit still returns to the page that asked for the login.
- **Regression test:** `LogoutRedirect.test.tsx` (2 tests). The first one failed with the old code (1 failed, 1 passed) and passes with the fix; the second checks that the redirect to the requested page still works.
- **Checked:** browser after the fix: `history.state.usr` after logout `null`; Gestor Demo then landed on `/`. The E2E main flow passes without its workaround.

### Not executed or limited

- The real reactivation login of a reactivated account (forced password change) was checked by `AdminApiTest`, not by hand.
- Network failure in the browser (backend down while submitting a form): not run by hand; the error path is the same as the 409 and 422 cases above and the 500 case is covered by `FailureHandlingTest`.
- Screen readers: not tested; axe-core finds only automatic issues.

## Increment 9 — Ex. 9 · review and delivery, 2026-11-17

### Diff review (from `27bcf77`, with AI support)

Focus asked by the Worksheet: permissions, validation, error handling and changes out of scope. The AI read the security filter chain, authentication, account administration, members, password reset and the access checks, and compared every route in the code with `openapi.yaml` (script over the `@*Mapping` annotations and the contract paths): 34 operations in both, none missing or extra.

| # | Finding | Severity | Decision |
|---|---|---|---|
| R-1 | Deactivating an account checks "only Manager" (D5) before locking the projects, so the other Manager being demoted at the same moment can leave a project with no Manager | Medium | **Fixed** (approved): `MemberService.lockProjectsOf` locks the user's projects before the check |
| R-2 | Spring Boot creates an unused in-memory user and logs "Using generated security password" at start-up (no access: form login and HTTP Basic are off) | Low | **Fixed** (approved): `UserDetailsServiceAutoConfiguration` excluded |
| R-3 | Contract limits not applied: reset request `email` ≤ 254 and `/admin/users?q=` ≤ 100 are accepted when longer | Low | **Fixed** (approved): 422 `TOO_LONG` |
| R-4 | Deactivation leaves pending password reset links valid; after a reactivation within 30 minutes an old link skips the initial password set by the Admin | Low | **Fixed** (approved): deactivation marks pending links as used |
| — | Login rate limit kept in memory (resets on restart, one instance only) | Known limitation | Documented in README; not changed |

| # | Check of the fixes | Type | Expected | Observed | Evidence |
|---|---|---|---|---|---|
| V-49 | R-1: deactivation and demotion at the same moment (D5, D16) | Auto | The project always keeps a Manager | Before the fix the new test failed in round 0 (both requests 200, 0 Managers left); after it, 10 rounds passed | `ConcurrencyTest.deactivatingAManagerWhileTheOtherIsDemotedNeverLeavesAProjectWithoutManager` |
| V-50 | R-2: no default user | Auto + Manual | No `UserDetailsService` bean; no generated password in the start-up log; login unchanged | As expected; start-up log of the new jar: 0 lines with "generated security password"; `AuthFlowTest` (13) and `SessionCookiesTest` pass | `HealthEndpointTest.thereIsNoDefaultInMemoryUser`; backend log |
| V-51 | R-3: contract limits | Auto | Email of 263 characters → 422 `TOO_LONG` and no email; `q` of 101 → 422, of 100 → 200 | As expected | `PasswordResetTest.anEmailLongerThanTheContractAllowsIsRefused`, `AdminApiTest.searchTextLongerThanTheContractAllowsIsRefused` |
| V-52 | R-4: old link after deactivation and reactivation | Auto | Old link → 422 `INVALID_TOKEN`; the Admin's initial password still works | As expected; without the fix the same test got 204 (old link accepted) | `PasswordResetTest.deactivationInvalidatesPendingLinks` |
| V-53 | Full suites after the fixes | Auto | Everything passes | `./mvnw verify` 86 tests, 0 failures; `npm test` 19; `npm run build` OK; `npm run e2e` 4 passed, 1 skipped | Commands |

No change out of scope was found; permissions (Admin, Manager, Member, non-member), CSRF, the account check on every request and generic error bodies match the documents.

### Refactoring (separate step)

- **Opportunity:** the dashboard project row (`ProjectRow` in `DashboardPage.tsx`) repeated the bar and legend markup of the shared `CountsBar` component, already used by the projects list and the project page.
- **Decision:** use `CountsBar` in the dashboard row and remove the copy and the unused `pct` helper (−20 lines). Benefit: one place for the status bar and its accessible label (NFR8); no behaviour change intended.
- **Behaviour preserved:**

| # | Check | Type | Expected | Observed | Evidence |
|---|---|---|---|---|---|
| V-47 | Same dashboard before and after | Manual | Same position and size of each row, bar and legend; same `aria-label`; same segment widths and legend text | Identical: e.g. "Annual report" bar 341×14 at (538, 684), "0 of 2 tasks done", segments 0% / 50% / 50%, legend "1 to do 1 in progress 0 done"; "Website redesign" "1 of 4 tasks done", 25% / 25% / 50% | `getBoundingClientRect` captured in the browser before and after the change, compared as JSON (`IDENTICAL`); screenshot before |
| V-48 | Tests after the refactoring | Auto | Everything passes | `npm test` 19 passed; `npm run build` OK; `npm run e2e` 4 passed, 1 skipped (Admin test, no test database) | Commands |

### Examples of AI use (objective, developer decision, verification)

1. **Own password change (Ex. 3).** *Objective:* plan the change of the user's own password. *AI suggestion:* refuse a new password equal to the current one in the API only (422 `SAME_AS_CURRENT`). *Decision (completed by the developer):* the interface must also refuse it with a clear message; the change-password page now checks it before sending and also shows the API answer. *Verification:* `AuthFlowTest` and `ChangePasswordPage.test.tsx`; tried in the browser. (`docs/ai-usage.md` #3)
2. **Light theme of the interface (Ex. 7).** *Objective:* a modern design with a vintage detail, from the developer's references. *AI suggestion:* wheat `#E6D3A3` background with a cream board. *Decision (corrected by the developer):* the background was too yellow; greige `#E3DED6` chosen from three options, then a light-stone board `#F2F0EC` instead of beige. *Verification:* options compared side by side in the design canvas; contrast checked for WCAG AA; axe-core audit in Ex. 8 with no serious violations. (`docs/ai-usage.md` #5)
3. **Bug after logout (Ex. 8).** *Objective:* find why an E2E test landed on the wrong page. *AI work:* reproduced it in the browser (`history.state` kept the previous page), located the cause in `RequireAuth`, and presented the diagnosis and a limited fix **before** changing files, as the Worksheet asks. *Decision:* the developer approved the fix with a regression test. *Verification:* the new test failed on the old code and passed with the fix; checked again in the browser; the E2E workaround was removed and the suite passed.

### Run from a clean copy (README steps)

| # | Check | Type | Expected | Observed | Evidence |
|---|---|---|---|---|---|
| V-54 | Fresh `git clone` of `feat/mvp` (commit `0b30476`), README followed step by step with its own `.env` (ports 5433, 1026/8026, 8082, 5175 so it did not clash with the developer's copy; separate Compose project `matriz-clean`, deleted afterwards) | Manual | Configuration, database, schema, seed, backend, frontend and tests work as written | `docker compose up -d`: db and Mailpit running; backend: Flyway V1, V2, V2.1 (demo data), `{"status":"UP"}`; `npm install` 0 vulnerabilities; frontend 200 and `/api` through the proxy; `./mvnw verify` 81 tests, 0 failures (the fixes came after this commit); `npm test` 19; `npm run build` OK; `npm run e2e` 4 passed, 1 skipped. **Problem found and fixed:** the README E2E command `source ../.env` failed (`parse error near '\n'`) because `.env.example` had `MATRIZ_MAIL_FROM=Matriz <no-reply@example.test>`; now `MATRIZ_MAIL_FROM=no-reply@example.test`, valid for the shell, Docker Compose and Spring; after the change the E2E run passed | Terminal output; logs |

## Worksheet 04 — Tests, Code Review and Security Review, 2026-11-17

Branch `test/quality-security` (from `main` at `v1.0.0`). Scenarios and expected results: [test-plan.md](test-plan.md); findings, evidence and decisions: [quality-security-review.md](quality-security-review.md). Test classes renamed in this worksheet: Worksheet 03 rows above keep the old `*Test` names (now `*IT`).

| # | Scenario | Type | Expected | Observed | Evidence |
|---|---|---|---|---|---|
| V-55 | Unit tests of the title rule (Ex. 2; T1–T5, T25) | Auto | Valid, boundary 3 and 150, too short, too long, blank, missing, Unicode spaces, emoji | 9 tests; first run found Q-1 and Q-2; after the fix 9 passed | `TextInputTest`; `cd backend && ./mvnw test` |
| V-56 | Separate unit and integration steps (Ex. 3, for Worksheet 05 CI) | Auto | `test` runs only `*Test` without Docker; `verify -DskipUTs` only `*IT` | 9 unit; 86 integration (at that commit); `verify` 95 | `backend/pom.xml` (Surefire, Failsafe) |
| V-57 | Create a task and read it back through the API (Ex. 3; T6, T9, T10, T27, T28) | Auto | 201 with `Location` and contract fields, GET equal, row in the database; blank title 422; missing or foreign resource 404 (same body); no session 401; demo Member 404 on "Annual report" | 7 tests; first run found Q-4 and a test problem (`instance` in the 404 body); after the fixes 7 passed | `TaskCreateAndReadIT` |
| V-58 | E2E: log in → open project → create task → see it in the list (Ex. 4; T7, T29) | Auto | Task listed with translated labels, "Criada hoje", total 1, still there after a reload; blank title refused, nothing saved | 2 passed, twice; all E2E 6 passed, 1 skipped (Admin) | `frontend/e2e/task-create.spec.ts`, isolated app (backend :8081 on database `matriz_e2e`, Vite :5174; database dropped afterwards) |
| V-59 | IDOR / broken access control with the two demo accounts (Ex. 5) | Manual | Member gets 404 on every resource of "Annual report" and 403 on Manager actions in "Website redesign"; data unchanged | 24/24 as expected; no IDOR confirmed | `curl` script, isolated app; quality-security-review.md, "Security Review" |
| V-60 | Static analysis available (Ex. 5) | Auto | No known vulnerable dependency; type-checked build | `npm audit` 0 vulnerabilities; `npm run build` OK; no SAST configured (Semgrep planned for Worksheet 05) | quality-security-review.md, "Static analysis" |
| V-61 | Fixes Q-1, Q-2, Q-4, Q-5, Q-6, each with a regression test | Auto | Each test fails without its fix and passes with it | As expected (`TextInputTest`, `TaskCreateAndReadIT.aNewTaskWithAssigneesHasNoLastChange`, `TasksSection.test.tsx`, `NulCharacterIT`) | quality-security-review.md, "Checks run" |
| V-62 | Full suites at the end of the worksheet | Auto | Everything passes | `./mvnw verify`: unit 9, integration 96, 0 failures, 0 skipped; `npm test` 20; `npm run build` OK; E2E 6 passed, 1 skipped (before the Q-6 fix, which changed no interface); contract lint valid (2 old warnings) | Commands |

Not checked in this worksheet: the Admin E2E test (needs `MATRIZ_E2E_ADMIN_PASSWORD` and a test-only database); Q-3 and the task edit form in a browser; backend dependency audit and SAST (Worksheet 05).

## Worksheet 05 — Docker, Docker Compose, CI and release candidate, 2026-11-17

Branch `chore/delivery` (from `main` at `v1.1.0`), pull request to `main`. Commands and full results: [quality-security-review.md](quality-security-review.md) (Semgrep, Trivy, Q-3, D49, Q-7) and [test-plan.md](test-plan.md). CI run links were given by the developer (private repository; their content was read by the developer, and the failing run's log was read by the agent).

| # | Scenario | Type | Expected | Observed | Evidence |
|---|---|---|---|---|---|
| V-63 | Analysis of the project (Ex. 1) | Auto | Existing tests pass before any change | `./mvnw verify`: unit 9, integration 96, 0 failures; `npm test` 20 passed; `docker compose config -q` OK | Commands run on 2026-11-17 |
| V-64 | Q-3 and rule D49 (before Ex. 2) | Auto | Regression tests fail without the fix and pass with it | Q-3 tests: 2 failures without the fix; with the fixes: unit 13, integration 97, Vitest 23, build OK, contract lint valid | Commit `42f0b8a` |
| V-65 | Docker images (Ex. 2) | Manual | Both images build and run; no credentials inside; non-root | `docker build` OK (backend 514 MB, frontend 93 MB); both containers `healthy`; through nginx: health 200, page and SPA route 200, demo login 200, `/projects` and `/dashboard` 200; no `MATRIZ_*` variable in the images; uid 10001 and 101 | Commit `b706570`; base image changed to Ubuntu 22.04 (jammy) because the local 24.04 base layer was corrupted after the disk filled |
| V-66 | Whole application with Docker Compose (Ex. 3) | Manual | Services healthy; components communicate; data persists | `config -q` OK; `build` OK; `up -d`: 4 services `healthy`; logs: 0 ERROR/WARN; login 200, project and task created (201); reset email reached Mailpit with a link to :8088; project still there after `down` and `up -d`, then deleted (204) | Commit `60a4ebe` |
| V-67 | Image scan and static analysis (Ex. 4) | Auto | No blocking HIGH/CRITICAL; no Semgrep finding | Semgrep: 94 files, 0 findings. Trivy before fixes: backend 14, frontend 13; after patch versions and `apk upgrade`: 0 blocking (2 `spring-webmvc` CVEs accepted until 2027-02-17) | Commit `9b2c1f6`; actionlint 0 issues |
| V-68 | CI pipeline on GitHub (Ex. 4) | Auto | Every job passes; Docker job only after the checks | First pull request run: backend failed (`ConcurrencyIT`, `[200, 403]`, finding Q-7), Docker job skipped; after the test fix every job passed | Run 44126288477 (green, commit `77e3ef0`); failing log read from the downloaded archive |
| V-69 | Controlled failure (Ex. 4, additional validation) | Auto | Pipeline fails when a test fails; Docker job does not run; green again after the revert | `TextInputTest.acceptsAValidTitle` changed on purpose: backend job red, Docker job skipped; after `git revert`: every job green | Runs 44131426927 (red, commit `27828f3`) and 44131734299 (green, commit `314c6a4`) |
| V-70 | Run from the README on a clean clone (Ex. 6) | Manual | Application starts with the documented commands and `.env.example` unchanged | Clone of `2ec4a98`: `config -q` OK, `build` OK, `up -d`: 4 services `healthy`; fresh database: 3 migrations applied, Admin account created; logs: 0 WARN/ERROR | Temporary clone, own Compose project and volume, deleted afterwards |
| V-71 | E2E and accessibility tests against the Compose application (Ex. 6) | Auto | All pass, including the Admin test (test-only database) | `MATRIZ_E2E_URL=http://localhost:8088 npx playwright test`: 7 passed, 0 skipped (axe light and dark, Admin, main flow, IDOR, task creation) | Admin password changed through the API on the throwaway database (fictitious value) |
| V-72 | Login rate limit through nginx (Ex. 6) | Auto | Many logins from one address in 15 minutes get "Demasiadas tentativas" | Third E2E run in a row: every login refused with "Demasiadas tentativas. Tente mais tarde."; passed again with `MATRIZ_LOGIN_RATELIMIT_MAXATTEMPTS=1000` in a local `compose.override.yaml` | Expected behaviour (A17 rate limit), documented in the README |
| V-73 | No credentials in versioned files (Ex. 6) | Auto | No secret in the repository or its history | `gitleaks` 8.30.1 (`git` mode, whole history, 42 commits): no leaks; `git grep` for secret patterns: none; no `.env`, `.pem` or `.key` tracked | — |
| V-74 | Final review fixes F-1 to F-3 (Ex. 6) | Manual | Security headers on pages; database and Mailpit not reachable from the network; one CI run per change | Headers present on `/`, `/assets/…`, SPA routes; 5432 refused on the network address, open on 127.0.0.1; workflow `push` only on `main`; E2E 7 passed again; Trivy on the new frontend image: exit 0; actionlint: 0 issues | [quality-security-review.md](quality-security-review.md), "final review" |

Not checked in Worksheet 05: the impact of the two accepted `spring-webmvc` CVEs; the build on another machine with the Ubuntu 24.04 base image; the CI run after the Ex. 6 fixes (to be seen on the next push).
