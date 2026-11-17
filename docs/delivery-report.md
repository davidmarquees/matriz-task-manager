# Delivery report — Worksheet 05 (Matriz 1.2.0-rc.1)

> Branch `chore/delivery` (from `main` at `v1.1.0`), pull request to `main`, 2026-11-17.
> Evidence: [verification.md](verification.md) V-63 to V-74; details in [quality-security-review.md](quality-security-review.md) and [test-plan.md](test-plan.md).
> Everything below was run on the developer's machine or in GitHub Actions; nothing is reported that was not run.

## 1. Changes

| Exercise | Change | Commit |
|---|---|---|
| Before Ex. 2 (Worksheet 04 open items) | Rule D49: control characters refused with 422 `INVALID_CHARACTER`; task title fields count characters (Q-3) | `42f0b8a` |
| Ex. 2 | `backend/Dockerfile` (Temurin 21 JDK build, JRE runtime, non-root, health check), `frontend/Dockerfile` (Node 24 build, nginx-unprivileged), `frontend/nginx.conf`, `.dockerignore` files | `b706570` |
| Ex. 3 | `docker-compose.yml` → `compose.yaml`: `db`, `mail`, `backend`, `frontend`, private network, health checks, start order; `MATRIZ_WEB_PORT` | `60a4ebe` |
| Ex. 4 | `.github/workflows/ci.yml`; `.trivyignore.yaml`; patched Tomcat, Jackson and PostgreSQL driver versions; `apk upgrade` in the frontend image; `ConcurrencyIT` fix (Q-7) | `9b2c1f6`, `77e3ef0` |
| Ex. 5 | README reorganised; Mermaid diagrams in `architecture.md`; `CHANGELOG.md`; release notes; verification evidence; version 1.2.0-rc.1, contract 0.8.0; Trivy database mirror and time limit in CI | `2ec4a98` |
| Ex. 6 | Final review fixes: nginx security headers (F-1), database and Mailpit on `127.0.0.1` only (F-2), no duplicate CI runs (F-3); this report | (this commit) |

## 2. Test and pipeline results

| Check | Result |
|---|---|
| Backend unit tests (`./mvnw test`) | 13 passed |
| Backend integration tests (`./mvnw verify -DskipUTs`, Testcontainers) | 97 passed |
| Frontend component tests (Vitest) and build | 23 passed; type-checked build OK |
| E2E and accessibility (Playwright + axe) on the Compose application | 7 passed, 0 skipped (also after the Ex. 6 fixes) |
| `npm audit` | Production dependencies: 0 vulnerabilities. All dependencies: `picomatch` ≤ 4.0.7 (HIGH, development tools only, no fix), accepted; report only in CI |
| OpenAPI lint (Redocly 2.79.0) | Valid; 2 warnings, unchanged since Worksheet 04 |
| Semgrep 1.185.0 | 0 findings (94 files) |
| Trivy 0.76.2 | 0 blocking HIGH/CRITICAL; 2 accepted (see section 5) |
| `gitleaks` 8.30.1 (whole history) | No leaks |
| Docker Compose from the README on a clean clone | 4 services healthy; fresh database migrated; logs without WARN/ERROR |
| GitHub Actions | Green on the pull request (run 44126288477); controlled failure red with the Docker job skipped (run 44131426927); green after the revert (run 44131734299) |

Not yet seen: the CI run with the Ex. 6 changes (next push).

## 3. Problems and solutions

| Problem | Cause | Solution |
|---|---|---|
| Docker stopped answering during the first image build | The machine's disk filled up; Docker Desktop stopped its engine and did not restart from the menu | Space freed by the developer; Docker Desktop fully quit and reopened (diagnosed from Docker Desktop's logs) |
| `exec format error` in every binary of `eclipse-temurin:21-jre` / `21-jdk` | The Ubuntu 24.04 base layer was stored truncated (0-byte files) when the disk was full; removing the images and the build cache did not clear it | Backend images use the Ubuntu 22.04 (jammy) variants of the same official images; works the same in CI |
| Trivy: 14 vulnerabilities in the backend, 13 in the frontend image | Old patch versions under Spring Boot 3.5.16; Alpine packages older than the fixes | Tomcat 10.1.62, Jackson 2.21.8, PostgreSQL driver 42.7.12; `apk upgrade`; 2 `spring-webmvc` CVEs accepted (no fix in Spring Framework 6.2) |
| First pull request run failed in `ConcurrencyIT` (`[200, 403]` instead of `[200, 409]`) | The test assumed both requests pass the Manager check before either is saved; on the slower runner the second came after the first was saved (no longer a Manager, 403) | Test accepts 409 or 403 and still checks that one Manager remains; D16 clarified (Q-7) |
| Docker CI job took about 20 minutes once (4 minutes before) | Probably the Trivy database download from its default registry (not confirmed from the log) | Public mirror, one shared cache for both images, 10-minute limit on the step |
| E2E failed on the third run in a row | Login rate limit (20 attempts per address in 15 minutes) working as designed | Documented: for repeated runs, raise the limit in a local `compose.override.yaml` |
| CI failed on the Ex. 6 commit at `npm audit` | New advisory for `picomatch` (development tools Vite and Vitest), no patched version | Blocking audit limited to production dependencies; full audit reported only (decision by David) |
| Final review F-1 to F-3 | See [quality-security-review.md](quality-security-review.md) | Fixed and checked again (E2E 7 passed) |

## 4. Use of AI agents

- An AI agent (Claude, through Claude Code on the developer's machine) analysed the project, proposed a plan for each exercise, and after the developer's approval wrote the Dockerfiles, Compose file, workflow, fixes, tests and documentation, ran every check and reported the real results.
- The developer approved every plan and change, made every commit, push and merge, ran the actions outside the agent's reach (restarting Docker Desktop, freeing disk space, opening the pull request) and passed on the CI results from the private repository.
- Decisions taken by the developer: rule D49 and the Q-3 fix; blocking `npm audit` on production dependencies only; nginx for the frontend; `curl` health check; Semgrep and Trivy in CI; accepting the two `spring-webmvc` CVEs until 2027-02-17; Ubuntu 22.04 base images; E2E kept local; fixing F-1 to F-3 and recording F-4.
- Corrected AI work: the concurrency test written with AI in Worksheet 03 (entry 9 in [ai-usage.md](ai-usage.md)). The agent also flagged that the version mentioned at the start ("v0.2.0") did not match the real tag (`v1.1.0`).
- Limitation: the agent reviewed code it had written itself, so the final review is not independent.

## 5. Limitations and pending items

- **Not deployed:** hosting and the production SMTP provider are still pending decisions; emails go only to the local Mailpit.
- **Accepted vulnerabilities:** CVE-2026-47884 and CVE-2026-47890 (`spring-webmvc` 6.2.19, CRITICAL) until 2027-02-17; the fix needs Spring Boot 4. Impact on Matriz not analysed.
- **Accepted development-only advisory:** `picomatch` ≤ 4.0.7 (GHSA-fc5q-39xc-vjpf), until a fix is published.
- **E2E not in CI:** they need the running application and Chrome and create data; run locally before each release.
- Sessions and the login rate limit are kept in memory (one backend instance).
- GitHub Actions pinned by version tag, not by commit SHA (F-4).
- Not checked: the build with the Ubuntu 24.04 base on another machine; whether every cross-module import in the backend goes through a service (architecture rule); the CI run with the Ex. 6 changes.
- Release: tag `v1.2.0-rc.1` to be created by the developer after the merge; the final `1.2.0` after acceptance.
