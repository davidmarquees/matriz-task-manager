-- Matriz MVP schema (docs/database.md, section 2).
-- Conventions: bigint identity keys, timestamptz in UTC, enumerations as varchar with CHECK.

CREATE TABLE users (
    id                    bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name                  varchar(100),
    email                 varchar(254) NOT NULL,
    password_hash         varchar(100),
    must_change_password  boolean      NOT NULL DEFAULT true,
    account_role          varchar(10)  NOT NULL,
    status                varchar(15)  NOT NULL DEFAULT 'ACTIVE',
    locale                varchar(5)   NOT NULL DEFAULT 'pt-PT',
    theme                 varchar(6)   NOT NULL DEFAULT 'SYSTEM',
    deactivated_at        timestamptz,
    failed_login_attempts integer      NOT NULL DEFAULT 0,
    locked_until          timestamptz,
    created_at            timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT users_account_role_ck CHECK (account_role IN ('ADMIN', 'MANAGER', 'MEMBER')),
    CONSTRAINT users_status_ck CHECK (status IN ('ACTIVE', 'DEACTIVATED', 'ANONYMISED')),
    CONSTRAINT users_locale_ck CHECK (locale IN ('pt-PT', 'en')),
    CONSTRAINT users_theme_ck CHECK (theme IN ('LIGHT', 'DARK', 'SYSTEM')),
    CONSTRAINT users_failed_login_attempts_ck CHECK (failed_login_attempts >= 0),
    -- name and password hash are NULL only for an anonymised account (section 5)
    CONSTRAINT users_name_ck CHECK (name IS NOT NULL OR status = 'ANONYMISED'),
    CONSTRAINT users_name_not_blank_ck CHECK (name IS NULL OR btrim(name) <> ''),
    CONSTRAINT users_password_hash_ck CHECK (password_hash IS NOT NULL OR status = 'ANONYMISED')
);

-- Unique email, case-insensitive, across all accounts (US9, D1)
CREATE UNIQUE INDEX users_email_lower_uq ON users (lower(email));

CREATE TABLE projects (
    id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name        varchar(100)  NOT NULL,
    description varchar(2000),
    created_by  bigint        NOT NULL REFERENCES users (id),
    version     integer       NOT NULL DEFAULT 0,
    created_at  timestamptz   NOT NULL DEFAULT now(),
    updated_at  timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT projects_name_not_blank_ck CHECK (btrim(name) <> '')
);

CREATE TABLE project_members (
    project_id   bigint      NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    user_id      bigint      NOT NULL REFERENCES users (id),
    project_role varchar(10) NOT NULL,
    added_at     timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (project_id, user_id),
    CONSTRAINT project_members_role_ck CHECK (project_role IN ('MANAGER', 'MEMBER'))
);

CREATE INDEX project_members_user_idx ON project_members (user_id);

CREATE TABLE tasks (
    id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    project_id  bigint        NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    title       varchar(150)  NOT NULL,
    description varchar(2000),
    status      varchar(15)   NOT NULL DEFAULT 'TODO',
    priority    varchar(10)   NOT NULL DEFAULT 'MEDIUM',
    due_date    date,
    created_by  bigint        NOT NULL REFERENCES users (id),
    version     integer       NOT NULL DEFAULT 0,
    created_at  timestamptz   NOT NULL DEFAULT now(),
    updated_at  timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT tasks_id_project_uq UNIQUE (id, project_id),
    CONSTRAINT tasks_title_length_ck CHECK (char_length(btrim(title)) BETWEEN 3 AND 150),
    CONSTRAINT tasks_status_ck CHECK (status IN ('TODO', 'IN_PROGRESS', 'DONE')),
    CONSTRAINT tasks_priority_ck CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH'))
);

-- Dashboard counts per status (FR9)
CREATE INDEX tasks_project_status_idx ON tasks (project_id, status);

CREATE TABLE task_assignees (
    task_id     bigint      NOT NULL,
    user_id     bigint      NOT NULL,
    project_id  bigint      NOT NULL,
    assigned_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (task_id, user_id),
    -- the assignee row always belongs to the task's project
    CONSTRAINT task_assignees_task_fk FOREIGN KEY (task_id, project_id)
        REFERENCES tasks (id, project_id) ON DELETE CASCADE,
    -- only project members can be assigned; removing a member removes their assignments (E1)
    CONSTRAINT task_assignees_member_fk FOREIGN KEY (project_id, user_id)
        REFERENCES project_members (project_id, user_id) ON DELETE CASCADE
);

CREATE INDEX task_assignees_member_idx ON task_assignees (project_id, user_id);

CREATE TABLE comments (
    id         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    task_id    bigint        NOT NULL REFERENCES tasks (id) ON DELETE CASCADE,
    author_id  bigint        NOT NULL REFERENCES users (id),
    body       varchar(2000) NOT NULL,
    created_at timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT comments_body_not_blank_ck CHECK (btrim(body) <> '')
);

CREATE INDEX comments_task_idx ON comments (task_id);

CREATE TABLE password_reset_tokens (
    id         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    bigint       NOT NULL REFERENCES users (id),
    token_hash varchar(100) NOT NULL UNIQUE,
    expires_at timestamptz  NOT NULL,
    used_at    timestamptz,
    created_at timestamptz  NOT NULL DEFAULT now()
);

CREATE INDEX password_reset_tokens_user_idx ON password_reset_tokens (user_id);

-- Append-only (FR1d); the application user has INSERT and SELECT only (V2).
CREATE TABLE user_audit_log (
    id             bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    actor_id       bigint       REFERENCES users (id),
    target_user_id bigint       NOT NULL REFERENCES users (id),
    action         varchar(30)  NOT NULL,
    project_id     bigint,
    project_name   varchar(100),
    old_value      jsonb,
    new_value      jsonb,
    created_at     timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT user_audit_log_action_ck CHECK (action IN (
        'ACCOUNT_CREATED', 'ACCOUNT_DEACTIVATED', 'ACCOUNT_REACTIVATED', 'ACCOUNT_ANONYMISED',
        'PASSWORD_SET_BY_ADMIN', 'PASSWORD_CHANGED', 'PASSWORD_RESET_REQUESTED', 'PASSWORD_RESET_COMPLETED',
        'MEMBER_ADDED', 'MEMBER_REMOVED', 'PROJECT_ROLE_CHANGED', 'MANAGER_APPOINTED',
        'ACCOUNT_LOCKED', 'PREFERENCES_CHANGED'))
);

CREATE INDEX user_audit_log_target_idx ON user_audit_log (target_user_id, created_at);

-- Append-only (FR13); no FK on project_id, so entries survive a project delete.
CREATE TABLE activity_log (
    id           bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    project_id   bigint       NOT NULL,
    project_name varchar(100) NOT NULL,
    actor_id     bigint       NOT NULL REFERENCES users (id),
    entity_type  varchar(15)  NOT NULL,
    entity_id    bigint       NOT NULL,
    action       varchar(15)  NOT NULL,
    changes      jsonb,
    created_at   timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT activity_log_entity_type_ck CHECK (entity_type IN ('PROJECT', 'MEMBER', 'TASK', 'ASSIGNEE', 'COMMENT')),
    CONSTRAINT activity_log_action_ck CHECK (action IN ('CREATED', 'UPDATED', 'DELETED', 'ADDED', 'REMOVED'))
);

CREATE INDEX activity_log_project_idx ON activity_log (project_id, created_at);
