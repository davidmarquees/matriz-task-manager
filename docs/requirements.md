# Requirements — Matriz

> Legend: **[F]** fact (stated by the client) · **[A]** assumption (defined by the team, to be confirmed with the client).
> Detailed assumptions and constraints in [constraints.md](constraints.md).

## 1. Vision and goals

Client description, verbatim: *"Precisamos de uma aplicação onde as nossas equipas possam organizar projetos, gerir tarefas e acompanhar o progresso do trabalho."*
Translation: *"We need an application where our teams can organise projects, manage tasks and track the progress of their work."*

Goals:
- G1 [F] Allow teams to organise projects.
- G2 [F] Allow managing tasks.
- G3 [F] Allow tracking the progress of work.

## 2. User types [A]

| Role | Description |
|---|---|
| Manager | Creates projects, manages each project's members and has every permission on their projects. |
| Member | Sees the projects they are a member of, creates and edits tasks (including ones they did not create) and comments. |

There is no self-service registration in the MVP: only pre-created accounts exist, including two demo accounts (one Manager and one Member).

## 3. Functional requirements

| ID | Requirement | Source | Priority |
|---|---|---|---|
| FR1 | Users log in with email and password and can log out. | [A] | High |
| FR2 | The Manager creates, edits and deletes projects (name mandatory, description optional). | G1 | High |
| FR3 | The Manager adds and removes members of a project. | G1 | High |
| FR4 | A user only sees the projects they are a member of. | [A] | High |
| FR5 | Project members create, edit and delete tasks. Fields: title (mandatory), description, status, priority, due date, assignees. | G2 | High |
| FR6 | Task status: TODO, IN_PROGRESS or DONE; free transitions. | [A] | High |
| FR7 | Task priority: LOW, MEDIUM or HIGH. | [A] | High |
| FR8 | A task can have several assignees, all members of the project. | [A] | High |
| FR9 | Per-project dashboard with the number of tasks in each status. | G3 | High |
| FR10 | Task text search (title and description) and filters by status, priority and assignee. | [A] | Medium |
| FR11 | Project members comment on tasks (plain text). | [A] | Medium |
| FR12 | There are two demo accounts: one Manager and one Member. | [A] | Medium |

### Permission rules [A]

| Action | Manager | Member |
|---|---|---|
| Create / edit / delete project | Yes | No |
| Manage project members | Yes | No |
| Create / edit task | Yes | Yes |
| Delete task | Yes | Only tasks they created |
| Comment | Yes | Yes |

## 4. Non-functional requirements [A]

| ID | Requirement |
|---|---|
| NFR1 | User interface in European Portuguese. |
| NFR2 | Sized for up to 50 users. |
| NFR3 | Passwords are never stored in plain text. |
| NFR4 | All permissions are enforced on the server, not only in the interface. |
| NFR5 | Clear error messages that do not expose internal details. |
| NFR6 | Destructive actions (deleting a project or task) ask for confirmation. |

## 5. MVP scope

FR1 to FR12, with tasks shown in a list view.

## 6. Out of initial scope

Kanban board, Team entity, notifications, attachments, subtasks, history and audit, project archiving, due-date alerts, self-service user registration, editing and deleting comments.
