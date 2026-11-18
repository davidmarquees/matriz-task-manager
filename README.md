# Matriz

Project and task management application for work teams: teams organise projects, manage tasks and track the progress of their work.

Public demo (Render + Neon, fictitious data): <https://matriz-web.onrender.com> — see [docs/deployment.md](docs/deployment.md).

Current version: **1.2.0-rc.1** (release candidate, see [CHANGELOG.md](CHANGELOG.md) and the [release notes](docs/release-notes/v1.2.0-rc.1.md)).

## Features

- Login with accounts created by an Admin, who assigns the Manager or Member role, and can deactivate, reactivate and anonymise them.
- Password reset by email and change of own password (mandatory at first login).
- Pre-created Admin account; demo Manager and Member accounts in the `dev` profile (for testing permissions).
- Creating, editing and deleting projects, with member management.
- Creating, editing and deleting tasks, with status (TODO, IN_PROGRESS, DONE) and priority (LOW, MEDIUM, HIGH).
- Multiple assignees per task.
- Comments on tasks.
- Task search and filters.
- Dashboard with task counts per status.
- Audit log of changes to users and activity log of changes inside each project.
- Interface in European Portuguese and English, with light and dark themes, responsive for desktop and mobile browsers.

These features are based on assumptions not yet confirmed by the client; see [docs/constraints.md](docs/constraints.md).

## Technologies

| Part | Technology (version in this repository) |
|---|---|
| Backend | Java 21, Spring Boot 3.5.16 (Web, Security, Data JPA, Validation, Actuator, Mail), Flyway, Maven Wrapper (Maven 3.9.12) |
| Database | PostgreSQL 16 |
| Frontend | React 18.3.1, TypeScript 5.9.3, Vite 6.4.6, React Router 7.18.5, react-i18next 18.0.2 |
| Tests | JUnit 5, Spring Boot Test, Testcontainers 1.21.4; Vitest 5.1.0 and Testing Library; Playwright 1.64.1 with axe-core 4.14.0 |
| Containers | Docker images: Eclipse Temurin 21 (Ubuntu 22.04) for the backend, Node 24 to build the frontend and nginx (unprivileged, Alpine) to serve it; Docker Compose for the whole application; Mailpit 1.27 as local mail catcher |
| CI | GitHub Actions; Semgrep 1.185.0 and Trivy 0.76.2 (official Docker images); Redocly CLI 2.79.0 for the API contract |

## Architecture

Modular monolith: one Java + Spring Boot backend split into business modules, a React frontend and one PostgreSQL database. It is the simplest option that fits the MVP (one deployment, one database, easy to test) while keeping the code organised by module. In containers, nginx serves the frontend and forwards `/api` to the backend, so the browser sees one origin. Diagrams, the comparison of options and the reasons: [docs/architecture.md](docs/architecture.md).

## Prerequisites

- **Docker** (Docker Desktop on macOS) with Docker Compose, running. Enough to run the whole application.
- For development and tests outside containers:
  - Java 21 (check with `java -version`; on macOS `export JAVA_HOME=$(/usr/libexec/java_home -v 21)`). Maven is not needed: the backend includes the Maven Wrapper (`./mvnw`).
  - Node.js 20 or later with npm (the images and the CI use Node 24).
  - Docker, also for the backend integration tests (Testcontainers).
  - Google Chrome, only for the end-to-end tests.

## Installation and configuration

```bash
git clone https://github.com/davidmarquees/matriz-task-manager.git
cd matriz-task-manager
cp .env.example .env
```

All configuration comes from environment variables, kept in the `.env` file at the repository root (git-ignored); real environment variables take precedence over it. Change the passwords in `.env`. `.env.example` has fictitious values only; never commit real credentials.

| Variable | Used by | Meaning |
|---|---|---|
| `MATRIZ_DB_NAME`, `MATRIZ_DB_PORT` | Docker Compose | Database name and port on the host |
| `MATRIZ_DB_OWNER_USER`, `MATRIZ_DB_OWNER_PASSWORD` | Compose, Flyway | Database owner; creates the schema |
| `MATRIZ_DB_APP_USER`, `MATRIZ_DB_APP_PASSWORD` | Compose, backend | Application user at runtime; `INSERT` and `SELECT` only on the logs |
| `MATRIZ_DB_URL` | Backend | JDBC URL, e.g. `jdbc:postgresql://localhost:5432/matriz` (Compose sets its own) |
| `MATRIZ_SERVER_PORT` | Backend | HTTP port (default: `PORT` if set, as on Render, otherwise 8080) |
| `MATRIZ_CORS_ALLOWED_ORIGINS` | Backend | Origins allowed to call the API from another site, comma-separated; empty (default) = none |
| `MATRIZ_COOKIE_SECURE` | Backend | `true` sends the session and CSRF cookies over HTTPS only; `false` only for local HTTP |
| `MATRIZ_PROFILE` | Backend | `dev` adds the demo data; leave empty in production |
| `MATRIZ_ADMIN_NAME`, `MATRIZ_ADMIN_EMAIL`, `MATRIZ_ADMIN_PASSWORD` | Backend | Admin account created at the first start if none exists |
| `MATRIZ_DEMO_PASSWORD` | Backend (`dev`) | Password of the two demo accounts |
| `MATRIZ_MAIL_HOST`, `MATRIZ_MAIL_PORT`, `MATRIZ_MAIL_USERNAME`, `MATRIZ_MAIL_PASSWORD` | Compose, backend | SMTP server for the reset email (Mailpit in development: `localhost`, `1025`, no user; Compose sets the host) |
| `MATRIZ_MAIL_UI_PORT` | Compose | Port of the Mailpit web page (default 8025) |
| `MATRIZ_MAIL_FROM` | Backend | Sender of the reset email |
| `MATRIZ_APP_URL` | Backend | Address of the frontend, used in the reset link (default `http://localhost:5173`; Compose sets `http://localhost:${MATRIZ_WEB_PORT}`) |
| `MATRIZ_WEB_PORT` | Docker Compose | Host port of the whole application in containers (default 8088) |
| `VITE_API_URL` | Frontend (build) | API address used by the browser (default `/api`) |
| `MATRIZ_BACKEND_URL` | Frontend (dev server) | Backend address for the Vite proxy |

## Running

### A. Whole application with Docker Compose

```bash
docker compose up -d --build
```

Builds the backend and frontend images and starts four services from [compose.yaml](compose.yaml) on a private network `matriz`:

| Service | Image | Role | Host port |
|---|---|---|---|
| `db` | `postgres:16` | Database, persistent volume `matriz-db-data`; creates the application user on the first start (`docker/postgres/init`) | `MATRIZ_DB_PORT` (5432), this machine only |
| `mail` | `axllent/mailpit:v1.27` | Catches every email; nothing leaves the machine | 1025 (SMTP), `MATRIZ_MAIL_UI_PORT` (8025, web), this machine only |
| `backend` | built from `backend/Dockerfile` | REST API on `/api`; starts after `db` is healthy | none (only nginx reaches it) |
| `frontend` | built from `frontend/Dockerfile` | nginx: serves the React build (with security headers) and forwards `/api` to `backend:8080`; starts after `backend` is healthy | `MATRIZ_WEB_PORT` (8088) |

Open <http://localhost:8088>. Useful commands:

```bash
docker compose ps                 # every service "healthy"
docker compose logs --tail=50     # recent logs of all services
docker compose down               # stop; the database is kept
docker compose down -v            # stop and DELETE the database volume
```

Each service has a health check (`pg_isready`, `mailpit readyz`, `GET /api/actuator/health`, `GET /`). The images run as non-root users and contain no credentials: values come from `.env` when the containers start. Behind nginx the backend trusts `X-Forwarded-For` (`SERVER_FORWARD_HEADERS_STRATEGY=native`), which nginx overwrites, so the login rate limit still works per client address.

### B. Development mode (backend and frontend outside Docker)

1. Database and Mailpit:

   ```bash
   docker compose up -d db mail
   ```

   `docker compose down` keeps the data; `docker compose down -v` deletes it. Every email the backend sends (only the password reset link) goes to Mailpit: read it at <http://localhost:8025>. If Mailpit is not running, asking for a reset still answers the same and the error is logged without the link.

2. Backend (schema and seed):

   ```bash
   cd backend
   ./mvnw spring-boot:run
   ```

   At start-up Flyway creates or updates the schema (`backend/src/main/resources/db/migration`). With `MATRIZ_PROFILE=dev` it also adds the demo data once (`db/seeddev`); later start-ups never repeat it and never delete data. The Admin account is created at the first start. Health check: <http://localhost:8080/api/actuator/health>.

3. Frontend:

   ```bash
   cd frontend
   npm install
   npm run dev
   ```

   Open <http://localhost:5173>. Calls to `/api` go through the Vite proxy to the backend.

Both modes use the same database volume. The Compose backend publishes no port, so modes A and B can run at the same time.

### Demo accounts (`dev` and `demo` profiles only, fictitious data)

The `demo` profile is used only by the public course deployment ([docs/deployment.md](docs/deployment.md)); it adds the same demo data as `dev` and nothing else.

| Account | Email | Password | Access |
|---|---|---|---|
| Gestor Demo | `manager@example.test` | `MATRIZ_DEMO_PASSWORD` | Projects "Website redesign" (Manager) and "Annual report" (Manager) |
| Membro Demo | `member@example.test` | `MATRIZ_DEMO_PASSWORD` | Project "Website redesign" (Member) only |
| Admin | `MATRIZ_ADMIN_EMAIL` | `MATRIZ_ADMIN_PASSWORD` | Account administration ("Contas" in the menu): create, deactivate, reactivate and anonymise accounts, appoint a project Manager, read the audit log; must change the password at first login |

## API documentation

The REST API is described in [openapi.yaml](openapi.yaml) (OpenAPI 3.1, contract version 0.8.0): endpoints, request and response schemas, error format and codes. To read it in a browser or check it (needs Node; nothing is added to the project):

```bash
npx --yes @redocly/cli@2.79.0 build-docs openapi.yaml -o /tmp/matriz-api.html   # one HTML page; open it in a browser
npx --yes @redocly/cli@2.79.0 lint openapi.yaml                                 # contract check (also run in CI)
```

### Session lifecycle

Authentication uses a server-side session (Spring Security), described in `openapi.yaml` (API description, "Session lifecycle"):

1. `GET /api/auth/me` without a session answers 401 and sets the `XSRF-TOKEN` cookie (readable by the SPA, `SameSite=Lax`).
2. `POST /api/auth/login` with `{email, password}` and the token in the `X-XSRF-TOKEN` header. On success the server creates a new session (`JSESSIONID`, HttpOnly, `SameSite=Lax`) and a new CSRF token. Every failure gets the same 401 ("Invalid email or password."). 5 failures in a row lock the account for 15 minutes; more than 20 attempts from one IP address in 15 minutes get 429.
3. Every later request sends the session cookie; POST, PATCH and DELETE also send `X-XSRF-TOKEN`. The account status is read from the database on every request; a deactivated account gets 401. While a password change is required, everything except `me`, `logout` and `password` gets 403.
4. The session ends with `POST /api/auth/logout`, after 30 minutes of inactivity, or when the user changes the password in another session (`POST /api/auth/password` ends every other session). Sessions are kept in memory, so restarting the backend ends them all. The interface then shows the login page with "A sessão expirou".

### Behind a reverse proxy (production)

The login rate limit (20 attempts per IP address in 15 minutes) uses the client address the backend sees. Behind a reverse proxy or load balancer every request would come from the proxy's address, so all users would share one limit. In that case (the Compose setup already does the first two):

- configure the proxy to send `X-Forwarded-For` / `X-Forwarded-Proto` and to overwrite any value sent by the client;
- start the backend with `SERVER_FORWARD_HEADERS_STRATEGY=native` (Spring Boot property `server.forward-headers-strategy`), so the client address comes from the trusted proxy header;
- set `MATRIZ_COOKIE_SECURE=true` (the proxy serves HTTPS).

Never trust forwarded headers when the backend is reachable without the proxy: a client could then choose its own address.

## Tests

Scenarios, expected results and the test linked to each: [docs/test-plan.md](docs/test-plan.md). Every command below runs without interaction.

| Level | Command | Needs | In CI |
|---|---|---|---|
| Backend unit tests (`*Test`, Surefire) | `cd backend && ./mvnw test` | Java 21 | Yes |
| Backend integration tests only (`*IT`, Failsafe) | `cd backend && ./mvnw verify -DskipUTs` | Java 21, Docker | Yes |
| Backend, all tests | `cd backend && ./mvnw verify` | Java 21, Docker | (same as the two above) |
| Frontend component tests and type-checked build | `cd frontend && npm test && npm run build` | Node | Yes |
| End-to-end and accessibility | see below | Running app, Chrome | No (local) |

Unit tests use no Spring context and no database. Integration tests use Testcontainers: each test class starts its own empty PostgreSQL container, so Docker must be running and tests never depend on earlier runs or on the development database. A single test class: `./mvnw test -Dtest=TextInputTest` or `./mvnw verify -DskipUTs -Dit.test=TasksApiIT`.

Frontend tests use Vitest and Testing Library in a simulated browser (jsdom); they mock the API, so they need no backend.

### End-to-end and accessibility tests (Playwright + axe)

They drive the installed Google Chrome against a running app, log in with the demo accounts, and run the axe-core audit (WCAG 2.1 A/AA rules) on the main pages in light and dark themes. Each test deletes the projects it creates. The Admin test also creates accounts, which cannot be deleted, so it runs only when `MATRIZ_E2E_ADMIN_PASSWORD` is set: use it with a database for tests only.

```bash
cd frontend
set -a; source ../.env; set +a
npm run e2e
```

One spec only: `npx playwright test e2e/task-create.spec.ts`. `MATRIZ_E2E_URL` changes the address (default <http://localhost:5173>, the development mode); for the Compose application use `MATRIZ_E2E_URL=http://localhost:8088`. To keep the development database clean, run a second backend on another port with its own database and point a second Vite server at it (`MATRIZ_BACKEND_URL=http://localhost:8081 npx vite --port 5174`, then `MATRIZ_E2E_URL=http://localhost:5174`). Many runs in a row reach the login rate limit (20 per 15 minutes); for a local test backend start it with `MATRIZ_LOGIN_RATELIMIT_MAXATTEMPTS=1000`; for the Compose application, add that variable to the `backend` environment in a local `compose.override.yaml` (Docker Compose reads it automatically; do not commit it). The HTML report is in `frontend/playwright-report` (git-ignored).

Why not in CI: they need the whole running application and Chrome, they create data (the Admin test cannot clean up), and many runs reach the login rate limit. The pipeline already blocks on unit, integration and component tests and on the image build; the E2E tests are run locally before a release.

## Continuous integration

[.github/workflows/ci.yml](.github/workflows/ci.yml) runs on pushes to `main` and on pull requests to `main`, with read-only permissions (`contents: read`) and no secrets:

1. In parallel: **backend** (unit tests, then integration tests and package), **frontend** (`npm ci`, tests, build, `npm audit` of the production dependencies; the full audit is shown but does not block), **contract** (OpenAPI lint) and **Semgrep** (Java, TypeScript and React rules; any finding fails).
2. Only if all of them pass, **docker**: `docker compose config -q` with `.env.example`, `docker compose build`, Trivy scan of both images (HIGH and CRITICAL with a published fix fail, except those accepted in [.trivyignore.yaml](.trivyignore.yaml)), start with `docker compose up --wait` and a smoke test through nginx.

Any failing step fails the pipeline, and the Docker job is skipped.

## Versions and releases

Versions follow [Semantic Versioning](https://semver.org/); tags `vX.Y.Z` mark them on `main` (`v1.0.0`, `v1.1.0`, release candidate `v1.2.0-rc.1`). Changes per version: [CHANGELOG.md](CHANGELOG.md).

## Documentation

| File | Content |
|---|---|
| [docs/requirements.md](docs/requirements.md) | Vision, users, functional and non-functional requirements, MVP scope |
| [docs/user-stories.md](docs/user-stories.md) | User stories, priorities, acceptance criteria, edge cases |
| [docs/constraints.md](docs/constraints.md) | Constraints, assumptions, security and privacy, pending decisions |
| [docs/architecture.md](docs/architecture.md) | Architecture decision, technology stack, components, diagrams (Mermaid) |
| [docs/database.md](docs/database.md) | Data model, ER diagram, business rules, error cases |
| [openapi.yaml](openapi.yaml) | REST API contract (OpenAPI 3.1) |
| [docs/ui/wireframe.md](docs/ui/wireframe.md) | Dashboard wireframes (desktop, mobile, light, dark) |
| [docs/implementation-plan.md](docs/implementation-plan.md) | Worksheet 03 increments, decisions and status |
| [docs/test-plan.md](docs/test-plan.md) | Test scenarios, levels and results |
| [docs/quality-security-review.md](docs/quality-security-review.md) | Findings, code and security reviews, static analysis and image scan |
| [docs/verification.md](docs/verification.md) | Verified scenarios: expected, observed, evidence |
| [docs/ai-usage.md](docs/ai-usage.md) | AI suggestions corrected or rejected by the developer |
| [CHANGELOG.md](CHANGELOG.md) | Changes per version |
| [docs/release-notes/v1.2.0-rc.1.md](docs/release-notes/v1.2.0-rc.1.md) | Release notes of the current release candidate |
| [docs/delivery-report.md](docs/delivery-report.md) | Worksheet 05 delivery report: changes, results, problems, AI use, limitations |
| [docs/deployment.md](docs/deployment.md) | Public deployment on Render + Neon: steps, variables, limitations, validation |
| [AGENTS.md](AGENTS.md) | Instructions for coding agents |

## Current status

Release candidate 1.2.0-rc.1 (Worksheet 05): the MVP of Worksheet 03 (authentication, projects, members, tasks, assignees, comments, dashboard, and the approved optional features), the tests and reviews of Worksheet 04, and the delivery setup of Worksheet 05 (Docker images, Docker Compose, CI pipeline). Verified in [docs/verification.md](docs/verification.md).

Known limitations:

- Sessions and the login rate limit are kept in memory: restarting the backend ends every session; one backend instance only.
- Public demo on Render + Neon free plans ([docs/deployment.md](docs/deployment.md)): the backend sleeps after 15 minutes without requests and takes about a minute to wake up; a restart ends every session.
- The production SMTP provider is still to decide: emails go to the local Mailpit in development; in the public demo they are sent only if `MATRIZ_MAIL_*` is set.
- `picomatch` ≤ 4.0.7 in the development tools (Vite, Vitest) has an advisory with no fix yet; not in the code sent to the browser.
- Two vulnerabilities in `spring-webmvc` 6.2.19 are accepted until 2027-02-17: the fix needs Spring Boot 4 (see [docs/quality-security-review.md](docs/quality-security-review.md)).
- End-to-end tests run locally, not in CI.
- GitHub Actions are pinned by version tag, not by commit SHA.
