#!/bin/sh
# Runs once, when the PostgreSQL data volume is created (docker-entrypoint-initdb.d).
# Creates the application database user; its privileges are granted by Flyway (V2__app_user_grants.sql).
# POSTGRES_USER is the database owner used by Flyway.
set -eu

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
     -v app_user="$MATRIZ_DB_APP_USER" -v app_password="$MATRIZ_DB_APP_PASSWORD" <<'EOSQL'
CREATE ROLE :"app_user" LOGIN PASSWORD :'app_password';
EOSQL
