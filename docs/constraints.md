# Constraints, Assumptions and Decisions — Matriz

## 1. Known constraints

- The client description is the only source of confirmed requirements (see [requirements.md](requirements.md), section 1).
- No code is implemented in Worksheet 01.
- Architecture and technology stack decided in Worksheet 02; see [architecture.md](architecture.md).
- The MVP must cover the scope required in Worksheet 03: authentication, project and task CRUD, TODO/IN_PROGRESS/DONE statuses, LOW/MEDIUM/HIGH priorities, multiple assignees per task, comments, search and filters, a dashboard with counts per status, and two demo accounts.
- All project documentation is written in English.

## 2. Assumptions (to be confirmed with the client)

| ID | Assumption |
|---|---|
| A1 | Roles are per project: Manager or Member. A project has one or more Managers (at least one at all times); its creator is the first. Managers can promote and demote members. Only Manager accounts can create projects. |
| A2 | Each user has their own account (email and password). There is no self-service registration: a global Admin creates accounts when someone joins the company and assigns the Manager or Member account role. Pre-created accounts: Admin, Manager, Member. |
| A3 | Each project has a list of members; there is no "Team" entity in the MVP. |
| A4 | A user only sees the projects they are a member of. |
| A5 | Mandatory task field: title; status and priority default to TODO and MEDIUM. |
| A6 | The title has 3 to 150 characters and cannot be made only of spaces. |
| A7 | Status transitions are free. |
| A8 | A task can have several assignees, all members of the project. |
| A9 | "Progress" = count of tasks per status. |
| A10 | Only the project's Managers delete the project; tasks are deleted by a project Manager or by their creator. Deletion is permanent, with confirmation. |
| A11 | Deleting a project deletes its tasks and comments. |
| A12 | The creator of a project automatically becomes a member. |
| A13 | A Member can edit tasks they did not create. |
| A14 | Comments cannot be edited or deleted in the MVP. |
| A15 | At least 50 users and 1,000 tasks must be supported with good performance; no delivery date defined. |
| A16 | Internal use, within a single company. |
| A17 | After 5 consecutive failed login attempts, the account is blocked for 15 minutes. |
| A18 | A session expires after 30 minutes of inactivity. |
| A19 | The due date is optional and any date is accepted (past dates included). |
| A20 | The description is optional and has at most 2,000 characters. |

## 3. Security and privacy

- Passwords stored only as a secure hash; never in plain text or in logs.
- All permissions enforced on the server.
- A user cannot access, even via direct URL, projects they are not a member of.
- Login messages do not reveal whether the email exists.
- Minimal personal data (name and email), in line with the GDPR.
- Demo accounts use fictitious data and must not exist in a production environment.
- Credentials and secrets are never stored in the repository.

## 4. Pending technical decisions

Decided in Worksheet 02 (see [architecture.md](architecture.md)): modular monolith; Java + Spring Boot, React, PostgreSQL; REST API described in `openapi.yaml`; authentication with a server-side session (Spring Security).

Still pending:
- Hosting and runtime environment.
- Testing strategy.

## 5. Pending product decisions

- How the passwords of the pre-created accounts (Admin, Manager, Member) are shared without storing them in the repository (e.g. documented outside the repo, or a `.env.example` with placeholder values only). To decide in Worksheet 03.
- How a user's personal data is deleted on request (GDPR right to erasure).

## 6. Scope limitations

- No kanban, notifications, attachments, subtasks, history, project archiving or due-date alerts.
- Comments cannot be edited or deleted.
- No self-service registration; the Admin only creates accounts (no editing or deactivation in the MVP).
- No Team entity; work is organised by project membership.
- Tasks are shown in a list view only.
