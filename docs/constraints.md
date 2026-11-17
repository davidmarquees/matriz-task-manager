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
| A21 | Passwords have at least 8 characters. |
| A22 | A password reset link expires after 30 minutes; at most 3 reset requests per email per hour. |
| A23 | Only the project's Managers read its activity log; Members do not. |
| A24 | Before login, the interface uses the browser's language if it is English, otherwise European Portuguese; after login, the language saved in the profile. Before login, the theme follows the system setting. |
| A25 | Demo accounts (Manager, Member) are created only in the development environment, without a forced password change, together with 2 demo projects that give them different access (one shared, one with the Manager only). The Admin account is created at the first start from environment variables and must change its password at the first login. |

## 3. Security and privacy

- Passwords stored only as a secure hash; never in plain text or in logs.
- All permissions enforced on the server.
- A user cannot access, even via direct URL, projects they are not a member of.
- Login messages do not reveal whether the email exists.
- Minimal personal data, in line with the GDPR: name, email, password hash, language and theme preferences, failed login attempts, and the user's actions in the two logs (`user_audit_log`, `activity_log`). The logs store ids and the names of changed personal fields, never their values (name, email), so anonymising an account also anonymises the logs.
- Demo accounts use fictitious data and must not exist in a production environment.
- Credentials and secrets are never stored in the repository.

## 4. Pending technical decisions

Decided in Worksheet 02 (see [architecture.md](architecture.md)): modular monolith; Java + Spring Boot, React, PostgreSQL; REST API described in `openapi.yaml`; authentication with a server-side session (Spring Security).

Still pending: see [architecture.md](architecture.md), section 7 (single list of pending technical decisions).

## 5. Pending product decisions

- Decided in Worksheet 03 (docs/implementation-plan.md, P6): the passwords of the pre-created accounts come from environment variables; the repository has only a `.env.example` with fictitious values.
- Email sending for password reset: decided in Worksheet 03 (Ex. 8) for development: Mailpit, a local test mail server in `docker-compose.yml`; no real emails are sent; sender `MATRIZ_MAIL_FROM`. Still pending: the SMTP provider and sender address for production.
- How long the two logs are kept (currently indefinitely). To decide with the client.

Decided in Worksheet 02: GDPR erasure is done by anonymising the deactivated account; its tasks and comments stay and show "Removed user" (see [database.md](database.md), section 5).

## 6. Scope limitations

- No kanban, notifications (the only email sent is the password reset link), attachments, subtasks, project archiving or due-date alerts.
- Comments cannot be edited or deleted.
- No self-service registration; the Admin creates, deactivates, reactivates and anonymises accounts (no editing of name, email or account role, and no deletion, in the MVP). Users change only their own password, language and theme.
- No Team entity; work is organised by project membership.
- Tasks are shown in a list view only.
