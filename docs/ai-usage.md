# AI Usage Log — Matriz

Record of AI suggestions that the developer corrected or rejected (Worksheet 03 onwards).
Each entry: request, AI suggestion, developer decision, verification.

| # | Date | Request | AI suggestion | Decision | Verification |
|---|---|---|---|---|---|
| 1 | 2026-11-16 | Worksheet 03, Ex. 1: propose the stack versions in `docs/implementation-plan.md` (P1) | Java 17, because it was the default `java` on the machine and the minimum for Spring Boot 3 | **Corrected:** use Java 21 (LTS). Plan P1 updated; Java 21 made the default in the developer's shell | `java -version` and `mvn -v` in a new shell report Java 21.0.9 |
| 2 | 2026-11-16 | Worksheet 03, Ex. 3: security improvements to the authentication plan | Five improvements, including an automated test of the 30-minute session timeout | **Partly accepted:** rate limit per IP, new session id and end of other sessions on password change, explicit `SameSite=Lax` and generic login messages were implemented; the session timeout test was **deferred** to a later moment | Accepted items covered by `AuthFlowTest`, `LoginRateLimitTest` and `SessionCookiesTest` (docs/verification.md V-07 to V-14) |
| 3 | 2026-11-16 | Worksheet 03, Ex. 3: own password change | AI plan refused a new password equal to the current one only in the API (422 `SAME_AS_CURRENT`) | **Completed by the developer:** the interface must also show a clear message for it; added to the change-password page (checked before sending and on the API answer) | Browser check: "A nova palavra-passe tem de ser diferente da atual." shown (V-13) |
