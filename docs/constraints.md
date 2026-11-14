# Constraints, Assumptions and Decisions — Matriz

## 1. Known constraints

- The client description is the only source of confirmed requirements (see [requirements.md](requirements.md), section 1).
- No code is implemented in Worksheet 01.
- The choice of language, framework, database and architecture is left for Worksheet 02.
- The MVP must cover the scope required in Worksheet 03: authentication, project and task CRUD, TODO/IN_PROGRESS/DONE statuses, LOW/MEDIUM/HIGH priorities, multiple assignees per task, comments, search and filters, a dashboard with counts per status, and two demo accounts.
- All project documentation is written in English.

## 2. Assumptions (to be confirmed with the client)

| ID | Assumption |
|---|---|
| A1 | There are two roles: Manager and Member. |
| A2 | Each user has their own account (email and password); only pre-created accounts exist. |
| A3 | Each project has a list of members; there is no "Team" entity in the MVP. |
| A4 | A user only sees the projects they are a member of. |
| A5 | Mandatory task field: title; status and priority default to TODO and MEDIUM. |
| A6 | The title has at most 200 characters. |
| A7 | Status transitions are free. |
| A8 | A task can have several assignees, all members of the project. |
| A9 | "Progress" = count of tasks per status. |
| A10 | Only the Manager deletes projects; tasks are deleted by the Manager or by their creator. Deletion is permanent, with confirmation. |
| A11 | Deleting a project deletes its tasks and comments. |
| A12 | The creator of a project automatically becomes a member. |
| A13 | A Member can edit tasks they did not create. |
| A14 | Comments cannot be edited or deleted in the MVP. |
| A15 | Up to 50 users; no delivery date defined. |
| A16 | Internal use, within a single company. |

## 3. Security and privacy

- Passwords stored only as a secure hash; never in plain text or in logs.
- All permissions enforced on the server.
- A user cannot access, even via direct URL, projects they are not a member of.
- Login messages do not reveal whether the email exists.
- Minimal personal data (name and email), in line with the GDPR.
- Demo accounts use fictitious data and must not exist in a production environment.
- Credentials and secrets are never stored in the repository.

## 4. Pending technical decisions

- Language and framework (backend and frontend).
- Database.
- Architecture and API design.
- Authentication and session management mechanism.
- Hosting and runtime environment.
- Testing strategy.

## 5. Scope limitations

- No kanban, notifications, attachments, subtasks, history, project archiving or due-date alerts.
- No self-service registration or account management in the application.
- No Team entity; work is organised by project membership.
- Tasks are shown in a list view only.
