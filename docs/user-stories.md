# User Stories — Matriz (MVP)

> Requirements in [requirements.md](requirements.md). Assumptions in [constraints.md](constraints.md).
> Messages quoted below are shown to the user in the interface language (NFR1); criteria check the behaviour, not the exact wording.

## 1. User stories and priorities

| ID | User story | Requirements | Priority |
|---|---|---|---|
| US1 | As a user, I want to log in with email and password, so that I only access my own projects. | FR1, FR4 | High |
| US2 | As a Manager, I want to create, edit and delete projects and manage their members, so that I can organise the team's work. | FR2, FR3 | High |
| US3 | As a project member, I want to create, edit and delete tasks with a title, status and priority, so that I can manage the work. | FR5–FR7 | High |
| US4 | As a project member, I want to assign a task to one or more members, so that it is clear who does it. | FR8 | High |
| US5 | As a project member, I want to see a dashboard with my projects and the number of tasks in each status, so that I can track progress. | FR9 | High |
| US6 | As a project member, I want to search tasks by text and filter them by status, priority and assignee, so that I find what I need. | FR10 | Medium |
| US7 | As a project member, I want to comment on a task, so that I can communicate in its context. | FR11 | Medium |
| US8 | As a Manager or Member testing the system, I want two demo accounts (Manager and Member), so that I can test the permissions. | FR12 | Medium |
| US9 | As an Admin, I want to create an account for each new employee and assign it the Manager or Member role, so that they can access Matriz with the right permissions. | FR1a | High |

## 2. Acceptance criteria

### US1 — Authentication
- **Given** an existing account, **When** I enter the correct email and password, **Then** I am logged in and see only the projects I am a member of.
- **Given** a wrong email or a wrong password, **When** I submit, **Then** I see the same generic error message in both cases, and it does not say which field was wrong.
- **Given** I am not logged in, **When** I open any internal page, **Then** I am redirected to the login page.

### US2 — Projects
- **Given** I am logged in with a Manager account, **When** I create a project with a name, **Then** the project appears in my list and I am one of its Managers.
- **Given** I am a Member of a project, **When** I try to delete it, **Then** the action is refused and the project still exists.
- **Given** I am a Manager of the project, **When** I delete the project and confirm, **Then** the project, its tasks and its comments no longer exist.

### US3 — Tasks
- **Given** I am a project member, **When** I create a task without a title, **Then** the task is not created and the title field is flagged as mandatory.
- **Given** a new task, **When** I create it without a status or priority, **Then** it is TODO and MEDIUM.
- **Given** I am a Member and did not create the task, **When** I try to delete it, **Then** the action is refused; editing it is allowed.

### US4 — Multiple assignees
- **Given** a task, **When** I assign two project members to it, **Then** both appear as assignees.
- **Given** a user who is not a project member, **When** I try to assign them, **Then** the action is refused.

### US5 — Dashboard
- **Given** a project with 3 TODO, 2 IN_PROGRESS and 1 DONE tasks, **When** I open the dashboard, **Then** I see 3, 2 and 1.
- **Given** the counts above, **When** I move a task from TODO to DONE and go back to the dashboard, **Then** I see 2 TODO, 2 IN_PROGRESS and 2 DONE.

### US6 — Search and filters
- **Given** a project with tasks titled "Fix login" and "Write report", **When** I search for "login", **Then** only "Fix login" is listed.
- **Given** tasks with different statuses and priorities, **When** I filter by status IN_PROGRESS and priority HIGH, **Then** only tasks matching both are listed.
- **Given** a task assigned to me and one that is not, **When** I filter by assignee = me, **Then** only my task is listed.

### US7 — Comments
- **Given** I am a project member, **When** I add a comment to a task, **Then** it appears on the task with my name and the date.
- **Given** I am not a member of the project, **When** I try to comment on one of its tasks, **Then** the action is refused.

### US8 — Demo accounts
- **Given** a fresh installation, **When** I log in with the demo Manager account, **Then** I can create a project.
- **Given** a fresh installation, **When** I log in with the demo Member account, **Then** I cannot create projects, and I cannot delete projects or tasks I did not create.

### US9 — Account creation (Admin)
- **Given** I am logged in as Admin, **When** I create an account with name, email, initial password and role Member, **Then** that person can log in and cannot create projects.
- **Given** an account already exists with an email, **When** I create another account with the same email, **Then** it is refused.
- **Given** I am logged in as a Manager or Member, **When** I try to create an account, **Then** the action is refused.

## 3. Exceptional and edge cases

| # | Situation | Expected behaviour |
|---|---|---|
| E1 | Removing from a project a member who is assigned to tasks | They are no longer assigned to those tasks; the tasks remain. |
| E2 | Project with no tasks | The dashboard shows 0, 0 and 0, without errors. |
| E3 | Search or filters with no results | "No tasks found" is shown. |
| E4 | Opening, via direct URL, a project I am not a member of | Access is refused without revealing its content. |
| E5 | Title made only of spaces, shorter than 3 or longer than 150 characters | It is refused. |
| E7 | Description longer than 2,000 characters | It is refused. |
| E6 | The last remaining Manager of a project tries to remove or demote themselves | It is refused; the project always keeps at least one Manager. |
