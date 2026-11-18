# Changelog

All notable changes to Matriz. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and versions follow [Semantic Versioning](https://semver.org/). Each version is a Git tag `vX.Y.Z` on `main`.

## [Unreleased]

Public course demo on Render + Neon ([docs/deployment.md](docs/deployment.md)).

### Added
- `render.yaml` (Render Blueprint): backend Docker web service and frontend Static Site on free plans, deployed from `main` after the CI checks pass; `/api/*` rewritten to the backend; security headers on the static files.
- `demo` profile (`application-demo.yml`): the demo data of `dev`, for the public demo only.
- CORS limited to the origins in the new variable `MATRIZ_CORS_ALLOWED_ORIGINS` (empty by default: no cross-origin access).
- `docs/deployment.md`: steps in Neon and Render, variable names, limitations, validation.

### Changed
- The backend listens on `PORT` when `MATRIZ_SERVER_PORT` is not set (Render).

## [1.2.0-rc.1] - 2026-11-17

Release candidate of Worksheet 05 (delivery). Release notes: [docs/release-notes/v1.2.0-rc.1.md](docs/release-notes/v1.2.0-rc.1.md).

### Added
- Docker images for the backend (Eclipse Temurin 21 JRE, non-root, health check on `/api/actuator/health`) and the frontend (React build served by nginx, which also forwards `/api` to the backend).
- Whole application with Docker Compose: `compose.yaml` with `db`, `mail`, `backend` and `frontend` on a private network, health checks and start order; new variable `MATRIZ_WEB_PORT` (default 8088).
- CI pipeline on GitHub Actions (`.github/workflows/ci.yml`): backend unit and integration tests, frontend tests, build and `npm audit`, OpenAPI lint, Semgrep, then Docker image build, Trivy image scan and a smoke test. Read-only permissions, no secrets.
- Validation rule D49: control characters (U+0000 to U+001F, U+007F) in text fields are refused with 422 and the new error code `INVALID_CHARACTER`; descriptions and comments still accept tab and line breaks. Interface message in Portuguese and English.
- Mermaid diagrams in `docs/architecture.md`; README reorganised (technologies, prerequisites, Docker Compose, API documentation, CI); this changelog and the release notes.

### Changed
- `docker-compose.yml` renamed to `compose.yaml`; local development now starts only `docker compose up -d db mail`.
- The CI pipeline runs on pushes to `main` and on pull requests to `main` (no duplicate runs, finding F-3).
- A NUL character (U+0000) in a text field now gets 422 `INVALID_CHARACTER` instead of 400 (other inputs, such as search and login email, still get 400).
- API contract version 0.7.0 → 0.8.0 (new error code).
- Dependency patch versions for known vulnerabilities: Tomcat 10.1.62, Jackson 2.21.8, PostgreSQL JDBC driver 42.7.12; Alpine packages upgraded in the frontend image.

### Fixed
- Task title fields in the interface count length in characters, so a 150-character title with an emoji can be typed in full and "a😀" is refused as too short (finding Q-3).
- Flaky concurrency test: two Managers demoting each other at once may get 409 or 403 for the refused request (finding Q-7, rule D16 clarified).

### Security
- nginx sends `X-Frame-Options`, `X-Content-Type-Options` and `Referrer-Policy` on the pages it serves (finding F-1).
- PostgreSQL and Mailpit ports in `compose.yaml` are published on `127.0.0.1` only (finding F-2).
- CI blocks on vulnerabilities in production npm dependencies; the full audit is reported only. `picomatch` ≤ 4.0.7 (GHSA-fc5q-39xc-vjpf, development tools only, no fix yet) is accepted.
- Two vulnerabilities in `spring-webmvc` 6.2.19 (CVE-2026-47884, CVE-2026-47890) are accepted until 2027-02-17 in `.trivyignore.yaml`: they are fixed only in Spring Framework 7 / Spring Boot 4.

## [1.1.0] - 2026-11-17

Worksheet 04: tests, code review and security review.

### Added
- Test plan (`docs/test-plan.md`) and quality and security review (`docs/quality-security-review.md`).
- Unit tests for task title validation; API integration tests for task creation and reading; E2E test of the task creation flow; regression tests for every fixed finding.
- Separate unit (`*Test`, Surefire, `./mvnw test`) and integration (`*IT`, Failsafe, `./mvnw verify -DskipUTs`) test runs.

### Fixed
- Unicode spaces count as blank and lengths count characters (code points) in every text field (Q-1, Q-2).
- A task created with assignees no longer shows a "last change" (Q-4).
- The task title field is flagged as invalid in the create form (Q-5).
- A NUL character in any input gets 400 instead of 500 (Q-6).

## [1.0.0] - 2026-11-17

Worksheet 03: MVP.

### Added
- Backend (Spring Boot modular monolith) and frontend (React) skeleton, database schema with Flyway, grants for the application user and development seed.
- Session login and logout, current user, password change (mandatory at first login), account lockout, login rate limit and access checks.
- Projects with search, pagination and members; tasks with filters, pagination, counts, assignees and comments; dashboard with task counts per status.
- Account administration by the Admin, user audit log, project activity log and password reset by email (Mailpit in development).
- Complete interface in Portuguese and English with light and dark themes and saved preferences; E2E and accessibility tests (Playwright + axe).

[1.2.0-rc.1]: https://github.com/davidmarquees/matriz-task-manager/compare/v1.1.0...v1.2.0-rc.1
[1.1.0]: https://github.com/davidmarquees/matriz-task-manager/compare/v1.0.0...v1.1.0
[1.0.0]: https://github.com/davidmarquees/matriz-task-manager/releases/tag/v1.0.0
