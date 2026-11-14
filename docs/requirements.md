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
| Admin | Global role. Creates a user account when someone joins the company and assigns it the Manager or Member account role. Deactivates and reactivates accounts. Can appoint a Manager in any project at any time. Does not see or manage tasks and comments. |
| Manager | Account allowed to create projects. Becomes a Manager of each project they create and has every permission on it. |
| Member | Sees the projects they are a member of, creates and edits tasks (including ones they did not create) and comments. |

Roles apply per project: a project has one or more Managers and always at least one. The creator is its first Manager; a Manager can promote a Member to Manager or demote a Manager to Member. Every other user added to the project is a Member.

There is no self-service registration: accounts are created by the Admin. Three accounts are pre-created: Admin, Manager and Member; the Manager and Member accounts are the two demo accounts.

## 3. Functional requirements

| ID | Requirement | Source | Priority |
|---|---|---|---|
| FR1 | Users log in with email and password and can log out. | [A] | High |
| FR1a | The Admin creates user accounts (name, email, initial password) and assigns each the Manager or Member account role. | [A] | High |
| FR1b | The Admin deactivates and reactivates user accounts. A deactivated user cannot log in, is removed from all projects and is hidden from member lists, selectors and search; their name stays on the tasks and comments they created, with a "left the company" label. A returning person keeps their account and email (reactivation); a person with a different email gets a new account. Deactivating the only Manager of a project is refused until another Manager is appointed. On a GDPR erasure request, the Admin anonymises a deactivated account: name and email are removed and past work shows "Removed user". | [A] | Medium |
| FR1c | A user who forgot their password requests a reset link by email from the login page; the link is single-use and expires after 30 minutes. The answer is the same whether or not the email exists. | [A] | High |
| FR1d | Every change to a user's account or memberships is recorded in an audit log (who, what, to whom, when, values before and after). Only the Admin can read it; nobody can change or delete it. | [A] | High |
| FR1e | A logged-in user changes their own password by entering the current and the new one. When the password was set by the Admin (new or reactivated account), the user must change it at the first login before doing anything else. | [A] | High |
| FR2 | A Manager account creates projects; the project's Managers edit and delete it (name mandatory, description optional). | G1 | High |
| FR3 | The project's Managers add and remove members, and promote or demote them between Member and Manager; a project always keeps at least one Manager. The Admin can also appoint any active user as a Manager of any project. | G1 | High |
| FR4 | A user only sees the projects they are a member of. | [A] | High |
| FR5 | Project members create and edit tasks, and delete them according to the permission rules below. Fields: title (mandatory, 3 to 150 characters, not only spaces, see A6), description (optional, max. 2,000 characters, see A20), status, priority, due date (optional, any date accepted), assignees. | G2 | High |
| FR6 | Task status: TODO, IN_PROGRESS or DONE; free transitions. | [A] | High |
| FR7 | Task priority: LOW, MEDIUM or HIGH. | [A] | High |
| FR8 | A task can have several assignees, all members of the project. | [A] | High |
| FR9 | Dashboard listing the projects the user is a member of, each with the number of tasks in each status. | G3 | High |
| FR10 | Task text search (title and description) and filters by status, priority and assignee. | [A] | Medium |
| FR11 | Project members comment on tasks (plain text). | [A] | Medium |
| FR12 | Three pre-created accounts exist: Admin, Manager and Member; the Manager and Member accounts are the demo accounts for testing permissions. | [A] | Medium |
| FR13 | Every change inside a project (project data, members, tasks, assignees, comments) is recorded in an activity log: who, what, when, values before and after. The project's Managers can read it; nobody can change or delete it. | [A] | High |

### Permission rules [A]

| Action | Admin | Project Manager | Member |
|---|---|---|---|
| Create / deactivate / reactivate / anonymise accounts | Yes | No | No |
| Change own password, language and theme | Yes | Yes | Yes |
| Read the user audit log | Yes | No | No |
| See project names and members | Yes | Yes | Yes (own projects) |
| Appoint a project Manager (adds the user to the project if needed) | Yes | Yes | No |
| Create / edit / delete project | No | Yes | No |
| Manage project members | No | Yes | No |
| See, create / edit task | No | Yes | Yes |
| Delete task | No | Yes | Only tasks they created |
| Read the project activity log | No | Yes | No |
| Comment | No | Yes | Yes |

## 4. Non-functional requirements [A]

| ID | Requirement |
|---|---|
| NFR1 | User interface in European Portuguese and English. Each user chooses the language, and the choice is saved in their profile; the default is European Portuguese. Enumerated values (status, priority, roles) are always shown as translated labels, never as API values such as `IN_PROGRESS`. |
| NFR2 | Must perform well with at least 50 users and 1,000 tasks; above that, performance is not guaranteed (nothing is blocked). |
| NFR3 | Passwords are never stored in plain text; reset tokens are stored only as a hash. |
| NFR4 | All permissions are enforced on the server, not only in the interface. |
| NFR5 | Clear error messages that do not expose internal details. |
| NFR6 | Destructive actions ask for confirmation: deleting a project or task, removing or demoting a member, deactivating or anonymising an account. |
| NFR7 | Responsive web application usable in desktop and mobile browsers (from 360 px wide); no native app. |
| NFR8 | Light and dark themes. Each user chooses light, dark or "follow the system" (default), and the choice is saved in their profile. Both themes meet WCAG 2.1 AA contrast (at least 4.5:1 for text). |

## 5. MVP scope

FR1 to FR13, with tasks shown in a list view.

## 6. Out of initial scope

Kanban board, Team entity, notifications, attachments, subtasks, project archiving, due-date alerts, self-service user registration, editing and deleting comments, editing accounts after creation (accounts are only deactivated, never deleted).
