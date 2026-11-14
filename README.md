# Matriz

Project and task management application for work teams.

## Problem

The client needs an application where their teams can organise projects, manage tasks and track the progress of their work.

## Main goals

- Organise projects.
- Manage tasks.
- Track the progress of work.

## Features planned for the MVP

- Login with accounts created by an Admin, who assigns the Manager or Member role.
- Pre-created Admin, Manager and Member accounts (the last two for testing permissions).
- Creating, editing and deleting projects, with member management.
- Creating, editing and deleting tasks, with status (TODO, IN_PROGRESS, DONE) and priority (LOW, MEDIUM, HIGH).
- Multiple assignees per task.
- Comments on tasks.
- Task search and filters.
- Dashboard with task counts per status.

These features are based on assumptions not yet confirmed by the client; see [docs/constraints.md](docs/constraints.md).

## Documentation

| File | Content |
|---|---|
| [docs/requirements.md](docs/requirements.md) | Vision, users, functional and non-functional requirements, MVP scope |
| [docs/user-stories.md](docs/user-stories.md) | User stories, priorities, acceptance criteria, edge cases |
| [docs/constraints.md](docs/constraints.md) | Constraints, assumptions, security and privacy, pending decisions |
| [AGENTS.md](AGENTS.md) | Instructions for coding agents |

## Current status

Specification phase (Worksheet 01). There is no code and no technical decisions yet; the architecture will be defined in the next phase.
