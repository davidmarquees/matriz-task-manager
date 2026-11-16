# Matriz

Project and task management application for work teams.

## Problem

The client needs an application where their teams can organise projects, manage tasks and track the progress of their work.

## Main goals

- Organise projects.
- Manage tasks.
- Track the progress of work.

## Features planned for the MVP

- Login with accounts created by an Admin, who assigns the Manager or Member role, and can deactivate, reactivate and anonymise them.
- Password reset by email and change of own password (mandatory at first login).
- Pre-created Admin, Manager and Member accounts (the last two for testing permissions).
- Creating, editing and deleting projects, with member management.
- Creating, editing and deleting tasks, with status (TODO, IN_PROGRESS, DONE) and priority (LOW, MEDIUM, HIGH).
- Multiple assignees per task.
- Comments on tasks.
- Task search and filters.
- Dashboard with task counts per status.
- Audit log of changes to users and activity log of changes inside each project.
- Interface in European Portuguese and English, with light and dark themes, responsive for desktop and mobile browsers.

These features are based on assumptions not yet confirmed by the client; see [docs/constraints.md](docs/constraints.md).

## Documentation

| File | Content |
|---|---|
| [docs/requirements.md](docs/requirements.md) | Vision, users, functional and non-functional requirements, MVP scope |
| [docs/user-stories.md](docs/user-stories.md) | User stories, priorities, acceptance criteria, edge cases |
| [docs/constraints.md](docs/constraints.md) | Constraints, assumptions, security and privacy, pending decisions |
| [docs/architecture.md](docs/architecture.md) | Architecture decision, technology stack, components |
| [docs/database.md](docs/database.md) | Data model, ER diagram, business rules, error cases |
| [openapi.yaml](openapi.yaml) | REST API contract (OpenAPI 3.1) |
| [docs/ui/wireframe.md](docs/ui/wireframe.md) | Dashboard wireframes (desktop, mobile, light, dark) |
| [docs/implementation-plan.md](docs/implementation-plan.md) | Worksheet 03 increments, decisions and status |
| [docs/verification.md](docs/verification.md) | Verified scenarios: expected, observed, evidence |
| [docs/ai-usage.md](docs/ai-usage.md) | AI suggestions corrected or rejected by the developer |
| [AGENTS.md](AGENTS.md) | Instructions for coding agents |

## Architecture

Modular monolith: one Java + Spring Boot backend split into business modules, a React frontend and one PostgreSQL database. It is the simplest option that fits the MVP (one deployment, one database, easy to test) while keeping the code organised by module. See [docs/architecture.md](docs/architecture.md) for the comparison and the reasons.

## Running locally

### Requirements

- Java 21 (check with `java -version`; on macOS `export JAVA_HOME=$(/usr/libexec/java_home -v 21)`). Maven is not needed: the backend includes the Maven Wrapper (`./mvnw`).
- Node.js 20 or later with npm.
- Docker (Docker Desktop on macOS), running.

### 1. Configuration

All configuration comes from environment variables. For local development, keep them in a `.env` file at the repository root (git-ignored); real environment variables take precedence over it.

```bash
cp .env.example .env
```

Change the passwords in `.env`. `.env.example` has fictitious values only; never commit real credentials.

| Variable | Used by | Meaning |
|---|---|---|
| `MATRIZ_DB_NAME`, `MATRIZ_DB_PORT` | Docker Compose | Database name and port on the host |
| `MATRIZ_DB_OWNER_USER`, `MATRIZ_DB_OWNER_PASSWORD` | Compose, Flyway | Database owner; creates the schema |
| `MATRIZ_DB_APP_USER`, `MATRIZ_DB_APP_PASSWORD` | Compose, backend | Application user at runtime; `INSERT` and `SELECT` only on the logs |
| `MATRIZ_DB_URL` | Backend | JDBC URL, e.g. `jdbc:postgresql://localhost:5432/matriz` |
| `MATRIZ_SERVER_PORT` | Backend | HTTP port (default 8080) |
| `MATRIZ_COOKIE_SECURE` | Backend | `true` sends the session and CSRF cookies over HTTPS only; `false` only for local HTTP development |
| `MATRIZ_PROFILE` | Backend | `dev` adds the demo data; leave empty in production |
| `MATRIZ_ADMIN_NAME`, `MATRIZ_ADMIN_EMAIL`, `MATRIZ_ADMIN_PASSWORD` | Backend | Admin account created at the first start if none exists |
| `MATRIZ_DEMO_PASSWORD` | Backend (`dev`) | Password of the two demo accounts |
| `VITE_API_URL` | Frontend | API address used by the browser (default `/api`) |
| `MATRIZ_BACKEND_URL` | Frontend (dev server) | Backend address for the Vite proxy |

### 2. Database

```bash
docker compose up -d
```

Starts PostgreSQL 16 with a persistent volume. On the first start it also creates the application user (`docker/postgres/init`). `docker compose down` keeps the data; `docker compose down -v` deletes it.

### 3. Backend (schema and seed)

```bash
cd backend
./mvnw spring-boot:run
```

At start-up Flyway creates or updates the schema (`backend/src/main/resources/db/migration`). With `MATRIZ_PROFILE=dev` it also adds the demo data once (`db/seeddev`); later start-ups never repeat it and never delete data. The Admin account is created at the first start. Health check: <http://localhost:8080/api/actuator/health>.

### 4. Frontend

```bash
cd frontend
npm install
npm run dev
```

Open <http://localhost:5173>. Calls to `/api` go through the Vite proxy to the backend.

### Session lifecycle

Authentication uses a server-side session (Spring Security), described in `openapi.yaml` (API description, "Session lifecycle"):

1. `GET /api/auth/me` without a session answers 401 and sets the `XSRF-TOKEN` cookie (readable by the SPA, `SameSite=Lax`).
2. `POST /api/auth/login` with `{email, password}` and the token in the `X-XSRF-TOKEN` header. On success the server creates a new session (`JSESSIONID`, HttpOnly, `SameSite=Lax`) and a new CSRF token. Every failure gets the same 401 ("Invalid email or password."). 5 failures in a row lock the account for 15 minutes; more than 20 attempts from one IP address in 15 minutes get 429.
3. Every later request sends the session cookie; POST, PATCH and DELETE also send `X-XSRF-TOKEN`. The account status is read from the database on every request; a deactivated account gets 401. While a password change is required, everything except `me`, `logout` and `password` gets 403.
4. The session ends with `POST /api/auth/logout`, after 30 minutes of inactivity, or when the user changes the password in another session (`POST /api/auth/password` ends every other session). Sessions are kept in memory, so restarting the backend ends them all. The interface then shows the login page with "A sessão expirou".

### Demo accounts (`dev` profile only, fictitious data)

| Account | Email | Password | Access |
|---|---|---|---|
| Gestor Demo | `manager@example.test` | `MATRIZ_DEMO_PASSWORD` | Projects "Website redesign" (Manager) and "Annual report" (Manager) |
| Membro Demo | `member@example.test` | `MATRIZ_DEMO_PASSWORD` | Project "Website redesign" (Member) only |
| Admin | `MATRIZ_ADMIN_EMAIL` | `MATRIZ_ADMIN_PASSWORD` | Account administration; must change the password at first login |

### Tests

```bash
cd backend
./mvnw verify
```

Integration tests use Testcontainers: each test class starts its own empty PostgreSQL container, so Docker must be running and tests never depend on earlier runs or on the development database.

```bash
cd frontend
npm run build
```

## Current status

Implementation (Worksheet 03). Done: increment 1 (project skeleton, database schema and demo data) and increment 2 (authentication and access rules). See [docs/implementation-plan.md](docs/implementation-plan.md).
