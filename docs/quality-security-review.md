# Quality and Security Review — Matriz (Worksheet 04)

> One row per finding. **Status:** Confirmed = reproduced by a test or a command · Suspicion = not reproduced yet.
> **Severity:** High = data exposure or a broken permission · Medium = wrong result in a normal flow · Low = edge case with little impact.
> **Decision** is David's; the agent proposes only. Production code is changed only after approval, with a regression test.

## Findings

| # | Finding | File | Evidence | Impact | Severity | Decision | Status |
|---|---|---|---|---|---|---|---|
| Q-1 | A task title made only of non-breaking spaces (U+00A0) is accepted, although A6 says a title cannot be only spaces | `backend/src/main/java/pt/matriz/web/TextInput.java` (`required`: `value.trim()`) | `TextInputTest.refusesATitleMadeOnlyOfNonBreakingSpaces` failed on 2026-11-17: "Expecting code to raise a throwable". `String.trim()` removes only characters up to U+0020. The contract pattern `^\s*\S.{1,148}\S\s*$` would refuse it (in JSON Schema, `\s` includes U+00A0). The database check `char_length(btrim(title))` also trims only ASCII spaces, so it does not catch it either | A task with an invisible title can be saved; the same helper validates project names and comments, so they are likely affected too (not tested) | Low | Pending (David) | Confirmed (unit level) |
| Q-2 | A task title of 150 characters that contains an emoji is refused as too long | `backend/src/main/java/pt/matriz/web/TextInput.java` (`trimmed.length()`) | `TextInputTest.countsAnEmojiAsOneCharacter` failed on 2026-11-17 with 422 `TOO_LONG`. `String.length()` counts UTF-16 units, so an emoji counts as 2. FR5 says "150 characters"; JSON Schema `maxLength` and PostgreSQL `char_length` both count characters (code points) | A valid title near the limit is refused when it contains emoji or other characters outside the Basic Multilingual Plane; the backend is stricter than the contract and the database | Low | Pending (David) | Confirmed (unit level) |

Proposed fix (not applied): in `TextInput`, treat Unicode space separators (including U+00A0) as spaces when trimming and checking for blank, and count code points instead of UTF-16 units. Regression tests: enable the two `@Disabled` tests in `TextInputTest`. Because the helper is shared, the fix would also apply to project names and comments; that needs a decision.

## Checks run

| Date | Command | Result |
|---|---|---|
| 2026-11-17 | `cd backend && ./mvnw test -Dtest=TextInputTest` (before `@Disabled`) | 9 tests: 7 passed, 1 failure (Q-1), 1 error (Q-2) |
| 2026-11-17 | `cd backend && ./mvnw test -Dtest=TextInputTest` | 9 tests, 0 failures, 2 skipped (Q-1, Q-2) |
| 2026-11-17 | `cd backend && ./mvnw verify` | 95 tests, 0 failures, 0 errors, 2 skipped; `BUILD SUCCESS` |

## Not checked

- Q-1 and Q-2 through the API and in the interface. The frontend may already trim or limit the field differently.
- Whether project names and comments show the same behaviour.
