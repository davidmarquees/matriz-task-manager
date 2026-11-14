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
| [AGENTS.md](AGENTS.md) | Instructions for coding agents |

## Architecture

Modular monolith: one Java + Spring Boot backend split into business modules, a React frontend and one PostgreSQL database. It is the simplest option that fits the MVP (one deployment, one database, easy to test) while keeping the code organised by module. See [docs/architecture.md](docs/architecture.md) for the comparison and the reasons.

## Current status

Design phase (Worksheet 02). There is no code yet.
