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
| Q-5 | When the title is missing, the create form shows an error message, but the title input itself is not marked as invalid (no `aria-invalid`, no link to the message) | `frontend/src/components/TasksSection.tsx` (`CreateTaskForm`: error in `Alert` `task-create-error`) | Code reading during Ex. 4; the E2E test checks the message only. axe (accessibility spec) passed, but it does not check this state | Screen-reader users may not know which field is wrong (US3 asks for the title field to be flagged) | Low | Pending (David) | Suspicion |
| Q-3 | The task title field in the interface limits the length in UTF-16 units, so a 150-character title with an emoji cannot be typed in full | `frontend/src/components/TasksSection.tsx` (`maxLength={TASK_TITLE_MAX}` on `task-title-input`) | Code reading only: the HTML `maxlength` attribute counts UTF-16 code units. Not reproduced in a browser | Same as Q-2, in the interface only; the API now accepts the title | Low | Recorded, not fixed (David, 2026-11-17) | Suspicion |

Fix of Q-1 and Q-2 (2026-11-17, approved by David): `TextInput` trims and detects blank text using `Character.isWhitespace` or `Character.isSpaceChar`, so Unicode spaces such as U+00A0 count as spaces, and it counts lengths in code points. The helper is shared, so the same rule now applies to task titles and descriptions, project names and descriptions, comments, and account names and emails created by the Admin. Regression tests: `TextInputTest.refusesATitleMadeOnlyOfNonBreakingSpaces` and `.countsAnEmojiAsOneCharacter`, no longer `@Disabled`. Not changed: the database `CHECK` (`btrim` trims only ASCII spaces), which would need a migration; the backend check enforces the rule (NFR4).

Fix of Q-4 (2026-11-17, approved by David): in `TaskService.lastChangesOf`, an `ASSIGNEE` entry is left out when the same task has a `CREATED` entry with the same timestamp (both are written in the creation transaction, where `now()` does not change). The activity log itself is unchanged (FR13): the entries are still written and shown in the project history. Regression test: `TaskCreateAndReadIT.aNewTaskWithAssigneesHasNoLastChange`, no longer `@Disabled`; `AssigneesAndCommentsApiIT.assigneeChangesCountAsTheLastChange` still passes, so assignees added later still count as a change.

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

## Not checked

- Q-1 and Q-2 through the API (only the unit level and the full suite were run). In the interface, JavaScript `trim()` already removes U+00A0, so Q-1 could only be reached through the API (code reading).
- Q-3 in a browser.
- Q-4 in the interface (expected: "Alterada hoje" on a new task created with assignees), and whether adding an assignee in the same request as an update is affected.
- Project names, comments and account fields with Unicode spaces or emoji: they use the same helper, but no test of their own.
