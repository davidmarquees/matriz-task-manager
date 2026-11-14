# User Stories — Matriz (MVP)

> Requirements in [requirements.md](requirements.md). Assumptions in [constraints.md](constraints.md).
> Messages quoted below are shown to the user in the interface language (NFR1); the exact wording is to be localised.

## 1. User stories and priorities

| ID | User story | Requirements | Priority |
|---|---|---|---|
| US1 | As a user, I want to log in with email and password, so that I only access my own projects. | FR1, FR4 | High |
| US2 | As a Manager, I want to create, edit and delete projects and manage their members, so that I can organise the team's work. | FR2, FR3 | High |
| US3 | As a project member, I want to create, edit and delete tasks with a title, status and priority, so that I can manage the work. | FR5–FR7 | High |
| US4 | As a project member, I want to assign a task to one or more members, so that it is clear who does it. | FR8 | High |
| US5 | As a project member, I want to see a dashboard with the number of tasks in each status, so that I can track progress. | FR9 | High |
| US6 | As a project member, I want to search tasks by text and filter them by status, priority and assignee, so that I find what I need. | FR10 | Medium |
| US7 | As a project member, I want to comment on a task, so that I can communicate in its context. | FR11 | Medium |
| US8 | As an evaluator, I want two demo accounts (Manager and Member), so that I can test the permissions. | FR12 | Medium |

## 2. Acceptance criteria

### US1 — Authentication
- **Given** an existing account, **When** I enter the correct email and password, **Then** I am logged in and see only the projects I am a member of.
- **Given** wrong credentials, **When** I submit, **Then** I see "Invalid credentials", without indicating which field was wrong.
- **Given** I am not logged in, **When** I open any internal page, **Then** I am redirected to the login page.

### US2 — Projects
- **Given** I am a Manager, **When** I create a project with a name, **Then** the project appears in my list and I am a member of it.
- **Given** I am a Member, **When** I try to delete a project, **Then** the action is refused.
- **Given** I am a Manager, **When** I delete a project and confirm, **Then** the project, its tasks and its comments no longer exist.

### US3 — Tasks
- **Given** I am a project member, **When** I create a task without a title, **Then** it is refused with a mandatory-field message.
- **Given** a new task, **When** I create it without a status or priority, **Then** it is TODO and MEDIUM.
- **Given** I am a Member and did not create the task, **When** I try to delete it, **Then** the action is refused; editing it is allowed.

### US4 — Multiple assignees
- **Given** a task, **When** I assign two project members to it, **Then** both appear as assignees.
- **Given** a user who is not a project member, **When** I try to assign them, **Then** the action is refused.

### US5 — Dashboard
- **Given** a project with 3 TODO, 2 IN_PROGRESS and 1 DONE tasks, **When** I open the dashboard, **Then** I see 3, 2 and 1.
- **Given** I move a task from TODO to DONE, **When** I go back to the dashboard, **Then** the counts reflect the change.

## 3. Exceptional and edge cases

| # | Situation | Expected behaviour |
|---|---|---|
| E1 | Removing from a project a member who is assigned to tasks | They are no longer assigned to those tasks; the tasks remain. |
| E2 | Project with no tasks | The dashboard shows 0, 0 and 0, without errors. |
| E3 | Search or filters with no results | "No tasks found" is shown. |
| E4 | Opening, via direct URL, a project I am not a member of | Access is refused without revealing its content. |
| E5 | Title made only of spaces or longer than 200 characters | It is refused. |
| E6 | The only Manager of a project tries to remove themselves | It is refused. |
