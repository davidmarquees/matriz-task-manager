# AI Usage Log — Matriz

Record of AI suggestions that the developer corrected or rejected (Worksheet 03 onwards).
Each entry: request, AI suggestion, developer decision, verification.

| # | Date | Request | AI suggestion | Decision | Verification |
|---|---|---|---|---|---|
| 1 | 2026-11-16 | Worksheet 03, Ex. 1: propose the stack versions in `docs/implementation-plan.md` (P1) | Java 17, because it was the default `java` on the machine and the minimum for Spring Boot 3 | **Corrected:** use Java 21 (LTS). Plan P1 updated; Java 21 made the default in the developer's shell | `java -version` and `mvn -v` in a new shell report Java 21.0.9 |
