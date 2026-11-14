# Architecture — Matriz (MVP)

> Legend: **[D]** confirmed decision · **[A]** assumption (to be confirmed) · **[P]** pending decision.
> Based on [requirements.md](requirements.md), [user-stories.md](user-stories.md) and [constraints.md](constraints.md).

## 1. Drivers

- Small MVP scope: FR1–FR12, one company (A16), at least 50 users and 1,000 tasks (NFR2, A15).
- All permissions enforced on the server (NFR4); project roles drive most rules.
- Small team, course timeline; the code is written with AI assistants in Worksheet 03.
- Simple to run locally and to test.

## 2. Options compared

| Criterion | Monolith | Modular monolith | Microservices |
|---|---|---|---|
| Fit for MVP size | Good | Good | Poor: far more than the scope needs |
| Deployment | One unit | One unit | Many services, gateway, service discovery |
| Data consistency | One database, local transactions | One database, local transactions | Distributed data; cascade deletes (A11) and member checks cross services |
| Internal structure | Risk of tangled code as it grows | Clear module boundaries | Strong boundaries, high cost |
| Testing and debugging | Simple | Simple | Hard (network, contracts, many processes) |
| Path to grow | Refactor needed later | Modules can be split out later | Already split |
| Operating cost | Low | Low | High |

## 3. Decision: modular monolith

**[D] One Spring Boot application, split into business modules, with one PostgreSQL database.** Approved in Worksheet 02.

### Why this option

1. **Size:** the MVP has 12 functional requirements, one company (A16) and at least 50 users and 1,000 tasks (NFR2). One application handles this load without scaling out.
2. **One deployment:** one backend, one frontend build and one database to run, locally and in the course environment.
3. **Data consistency:** one database gives local transactions for rules that touch several areas, such as deleting a project with its tasks and comments (A11) or removing a member from their tasks (E1).
4. **Simpler security:** authorization (NFR4) is checked in one process, with no tokens passed between services.
5. **Organised code:** module boundaries (accounts, projects, tasks, comments, dashboard) keep the code readable for the team and for AI assistants, which a plain monolith does not enforce.
6. **Room to grow:** a module can be extracted into a service later if a real need appears.

### Why not the others

- **Monolith (no modules):** works for the MVP, but nothing stops the code from becoming tangled.
- **Microservices:** separate deployments, a gateway, data spread across services and network calls between them. That cost brings no benefit at this scale.

## 4. Technology stack

| Layer | Choice | Status | Justification |
|---|---|---|---|
| Backend | Java + Spring Boot (REST API) | [D] | Mature, strong support for validation (Bean Validation), security (Spring Security) and data access (Spring Data JPA); fits the team's preference. |
| Frontend | React (single-page application) | [D] | Team preference; fits a few screens with forms, lists and filters. |
| Database | PostgreSQL | [D] | Relational data with clear relations (projects, members, tasks, assignees, comments) and constraints (unique email, no duplicate assignees); built-in text search is enough for FR10. |
| API contract | REST + JSON, described in OpenAPI 3.1 (`openapi.yaml`) | [D] | Shared contract between frontend and backend. |
| Authentication | Server-side session with an HttpOnly cookie (Spring Security) | [D] | Must be the simplest option. Spring Security's default session gives login, logout and the 30-minute inactivity timeout (A18) with no extra infrastructure; no tokens stored in the browser. Alternative: JWT. |
| Password hashing | BCrypt (Spring Security) | [A] | Meets NFR3. |
| Schema migrations | Flyway | [A] | Versioned schema. Demo accounts are seeded only in the development profile (separate Flyway location); the Admin account is created at first start from environment variables (A25). |

## 5. Components and responsibilities

```
React SPA (responsive; pt-PT and en UI)
      │  HTTPS, JSON (REST)
      ▼
Spring Boot application (modular monolith)
  ├── web / api layer ──── controllers, request validation, error format (NFR5)
  ├── security ─────────── authentication, sessions, login lockout, authorization checks
  ├── accounts module ──── users, account roles, deactivation, password reset
  ├── audit module ─────── append-only logs: user changes and project activity
  ├── email adapter ────── sends the password reset email (SMTP)
  ├── projects module ──── projects, project members, project roles
  ├── tasks module ─────── tasks, assignees, search and filters
  ├── comments module ──── task comments
  └── dashboard module ─── task counts per status (read-only)
      │  JPA / SQL
      ▼
PostgreSQL
```

| Component | Responsibilities | Requirements |
|---|---|---|
| Frontend (React SPA) | Responsive screens for desktop and mobile browsers; texts in European Portuguese and English (translation files), including the labels for status, priority and roles; light and dark themes (CSS variables) with WCAG AA contrast; forms and client-side validation (for usability only), confirmation before destructive actions, redirect to login when not authenticated. | NFR1, NFR6–NFR8, US1 |
| API layer | REST endpoints, input validation, consistent error responses without internal details, HTTP status codes. | NFR5 |
| Security | Login and logout, session timeout, lockout after 5 failed attempts, generic login error, per-request authorization (account role and project role). | FR1, NFR3, NFR4, A17, A18 |
| Accounts | Admin creates, deactivates, reactivates and anonymises accounts; unique email; password reset with single-use tokens; own password change, forced at first login after an Admin-set password; creation of the pre-created accounts. | FR1a–FR1e, FR12, US9 |
| Audit | Records every change to a user's account or memberships (read by the Admin) and every change inside a project (read by its Managers), in the same transaction as the change. | FR1d, FR13 |
| Email adapter | Sends the reset email through SMTP, using backend email templates in European Portuguese and English chosen by `users.locale`; behind an interface so the provider can change. | FR1c, NFR1 |
| Projects | Project CRUD by Managers, membership, promote and demote, at least one Manager per project, visibility limited to members. | FR2–FR4, E4, E6 |
| Tasks | Task CRUD with defaults (TODO, MEDIUM), delete rules, several assignees who must be members, search and filters. | FR5–FR8, FR10, E1, E5 |
| Comments | Add and list comments with author and date; no edit or delete. | FR11, A14 |
| Dashboard | For each project of the user, task counts per status, plus totals across those projects. | FR9, E2 |
| Database | Persistence, integrity constraints, cascade delete of a project's tasks and comments. | A11 |

### Module rules [D]

- Each module owns its own tables; other modules use its service interface, not its repositories.
- Authorization is checked in the backend services, never only in the frontend (NFR4).
- Frontend and backend communicate only through the documented REST API.

## 6. Assumptions

- [A] The frontend is a separate React build served as static files; in development it calls the backend through a proxy.
- [A] One environment for the course (local); demo accounts exist only outside production.
- [A] No caching, message queues or background jobs are needed for NFR2.
- [A] The API returns codes (enum values, error codes), never translated text; screen translations live in the frontend. The only translated text in the backend is the password reset email.

## 7. Pending decisions

Single list of pending technical decisions (constraints.md links here).

- [P] Hosting and runtime environment (e.g. Docker Compose locally).
- [P] Testing strategy (unit, integration, end-to-end).
- [P] SMTP provider for the reset email (local test server proposed for the course).
- [P] Exact versions (Java, Spring Boot, React, PostgreSQL).
- [P] How the pre-created account passwords are shared (constraints.md, section 5).
