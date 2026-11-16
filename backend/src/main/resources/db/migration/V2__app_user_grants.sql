-- Privileges of the application database user (placeholder appUser = MATRIZ_DB_APP_USER).
-- The user itself is created outside Flyway (docker/postgres/init), because it has a password.
-- Rule for later migrations: every new table gets its own explicit GRANT here or in that migration.

GRANT USAGE ON SCHEMA public TO ${appUser};

GRANT SELECT, INSERT, UPDATE, DELETE ON
    users, projects, project_members, tasks, task_assignees, comments, password_reset_tokens
    TO ${appUser};

-- Append-only logs: no UPDATE or DELETE (D39)
GRANT SELECT, INSERT ON user_audit_log, activity_log TO ${appUser};

-- Identity columns
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO ${appUser};
