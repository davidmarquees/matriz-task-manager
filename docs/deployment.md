# Deployment: Render + Neon (public course demo)

Public deployment of Matriz with HTTPS on free plans, for the final presentation. Decided after Worksheet 05 (see [architecture.md](architecture.md), section 7).

> This is a **demo environment**: it runs the `demo` profile, so it has the two demo accounts with fictitious data. A real production environment would run without that profile.

## 1. Overview

| Part | Where | How |
|---|---|---|
| Frontend (React build) | Render **Static Site** `matriz-web` | `npm ci && npm run build` in `frontend/`, `VITE_API_URL=/api` at build time; Render CDN serves `frontend/dist` over HTTPS |
| Backend (Spring Boot) | Render **Web Service** `matriz-api`, Docker, free plan (512 MB) | Built from `backend/Dockerfile` (unchanged); health check `GET /api/actuator/health` |
| Database | **Neon** PostgreSQL 16 | SSL (`sslmode=require`); Flyway migrations at every start |
| Configuration | [`render.yaml`](../render.yaml) (Render Blueprint) | Versioned settings; secrets only in the Render Dashboard (`sync: false`) |

```
browser ──HTTPS──> matriz-web.onrender.com ──/api/* rewrite──> matriz-api.onrender.com ──SSL──> Neon
                   (static files, SPA)                         (Spring Boot, /api)
```

**Why the `/api` rewrite and not direct calls with CORS.** Login uses a server-side session: an HttpOnly session cookie and a CSRF token that the SPA reads from the `XSRF-TOKEN` cookie (`frontend/src/api/client.ts`) and sends with `credentials: 'same-origin'`. `onrender.com` is on the Public Suffix List, so `matriz-web.onrender.com` and `matriz-api.onrender.com` are different *sites*: a page on one cannot read the other's cookies, and browsers block its `SameSite=Lax` cookies on cross-site calls. The Static Site rewrite keeps the API on the frontend's origin, as nginx does in `compose.yaml`. CORS is still configured (`MATRIZ_CORS_ALLOWED_ORIGINS`, only the frontend's address) as a second layer; it is off when the variable is empty, as in local development.

**Local environment unchanged.** Docker Compose, the Dockerfiles, `nginx.conf` and `.env` keep working as before: the new settings are a profile (`demo`) and environment variables with the old defaults.

## 2. What the repository provides

| File | Change |
|---|---|
| `render.yaml` | Blueprint: both services, free plan, Frankfurt, deploy from `main` only after the CI checks pass (`autoDeployTrigger: checksPass`), rewrites, security headers, environment variable names |
| `backend/src/main/resources/application.yml` | `server.port: ${MATRIZ_SERVER_PORT:${PORT:8080}}` (Render sets `PORT`, default 10000); new property `matriz.cors-allowed-origins` |
| `backend/src/main/resources/application-demo.yml` | New `demo` profile: only adds the demo seed (`db/seeddev`), the same data as `dev` |
| `backend/src/main/java/pt/matriz/security/SecurityConfig.java` | CORS only for the origins in `MATRIZ_CORS_ALLOWED_ORIGINS` (GET, POST, PATCH, DELETE; `Content-Type`, `X-XSRF-TOKEN`; credentials) |
| Tests | `CorsIT` (allowed and refused origin), `DemoProfileIT` (demo data with the `demo` profile) |

## 3. Steps in the web dashboards (David)

Never paste credentials into the repository, a commit, an issue or a chat. Type them only in the Neon and Render dashboards.

### 3.1 Neon

1. Create a project: PostgreSQL **16**, region **AWS Europe Central 1 (Frankfurt)**, close to Render Frankfurt. Database name: `neondb` (default) or `matriz`.
2. Note the **owner** role (e.g. `neondb_owner`) and its password: these are `MATRIZ_DB_OWNER_USER` / `MATRIZ_DB_OWNER_PASSWORD` (Flyway creates the schema with it).
3. Create the **application role** in the **SQL Editor** (not in the Roles page: roles created there get `neon_superuser`, which would give the application `UPDATE`/`DELETE` on the logs and break rule D39). Run, as the owner, `CREATE ROLE matriz_app LOGIN PASSWORD '<password>';` with a long random password made on your machine (e.g. `openssl rand -base64 24`). Flyway's `V2__app_user_grants.sql` gives it its privileges at the first start. These are `MATRIZ_DB_APP_USER` / `MATRIZ_DB_APP_PASSWORD`.
4. Copy the **direct** connection host (Connect → turn **off** "Connection pooling"; the host has no `-pooler`). Flyway and the in-memory sessions need a normal connection.

### 3.2 Convert the Neon connection string to JDBC

Neon shows: `postgresql://<user>:<password>@<host>/<database>?sslmode=require&channel_binding=require`

`MATRIZ_DB_URL` keeps only the host and the database, with SSL; user and password go into their own variables:

```
jdbc:postgresql://<host>/<database>?sslmode=require
```

Example with fictitious values: `jdbc:postgresql://ep-example-123456.eu-central-1.aws.neon.tech/neondb?sslmode=require`.

### 3.3 Render

1. Sign in with GitHub and give Render access to the private repository `matriz-task-manager` (GitHub app, this repository only).
2. **New → Blueprint**, repository `matriz-task-manager`, branch `main` (after the deployment branch is merged). Render reads `render.yaml` and lists `matriz-api` and `matriz-web`.
3. Fill in the `sync: false` values (section 4). Render asks for them **only when the Blueprint is created**; later changes are made in each service's **Environment** page.
4. **Apply**. The first backend build takes several minutes (Maven inside Docker).
5. Check the addresses Render gave the two services. If they are not `matriz-api.onrender.com` and `matriz-web.onrender.com` (name taken), update `MATRIZ_APP_URL`, `MATRIZ_CORS_ALLOWED_ORIGINS` and the `/api/*` rewrite destination in `render.yaml` (and in the Dashboard), then deploy again.
6. Check that **Auto-Deploy** is "After CI Checks Pass" on both services (Settings → Build & Deploy).
7. First test: open `https://matriz-web.onrender.com`, log in as `manager@example.test` (section 6). If login fails while `https://matriz-api.onrender.com/api/actuator/health` answers `{"status":"UP"}`, the rewrite is not passing cookies or POST requests: see section 8.

## 4. Environment variables (names only)

### `matriz-api` (backend)

| Variable | Set in | Value or source |
|---|---|---|
| `MATRIZ_DB_URL` | Dashboard | JDBC URL from section 3.2 |
| `MATRIZ_DB_OWNER_USER`, `MATRIZ_DB_OWNER_PASSWORD` | Dashboard | Neon owner role |
| `MATRIZ_DB_APP_USER`, `MATRIZ_DB_APP_PASSWORD` | Dashboard | Neon role `matriz_app` (section 3.1, step 3) |
| `MATRIZ_DEMO_PASSWORD` | Dashboard | Password of both demo accounts (at least 8 characters; read once, when the seed runs) |
| `MATRIZ_ADMIN_NAME`, `MATRIZ_ADMIN_EMAIL`, `MATRIZ_ADMIN_PASSWORD` | Dashboard | Admin account created at the first start (fictitious address, e.g. `admin@example.test`); must change the password at first login |
| `MATRIZ_MAIL_HOST`, `MATRIZ_MAIL_PORT`, `MATRIZ_MAIL_USERNAME`, `MATRIZ_MAIL_PASSWORD`, `MATRIZ_MAIL_FROM` | Dashboard | Reset email, optional (section 7) |
| `MATRIZ_PROFILE` | `render.yaml` | `demo` (keep it: see section 5) |
| `MATRIZ_COOKIE_SECURE` | `render.yaml` | `true` |
| `MATRIZ_APP_URL`, `MATRIZ_CORS_ALLOWED_ORIGINS` | `render.yaml` | `https://matriz-web.onrender.com` |
| `SERVER_FORWARD_HEADERS_STRATEGY` | `render.yaml` | `native` (client address from the Render proxy, for the login rate limit) |
| `JAVA_TOOL_OPTIONS` | `render.yaml` | `-XX:MaxRAMPercentage=75 -XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError` (512 MB container) |
| `PORT` | Render | Set by Render (10000); the backend listens on it |

### `matriz-web` (frontend)

| Variable | Set in | Value |
|---|---|---|
| `VITE_API_URL` | `render.yaml` | `/api` (build time; not a secret, ends up in the JavaScript bundle) |
| `NODE_VERSION` | `render.yaml` | `24` (as `frontend/Dockerfile`) |

The frontend has no secrets: everything in `VITE_*` is public.

## 5. Migrations and demo data

- Flyway runs at every start of the backend, as the owner role. It applies only migrations that are not yet in its `flyway_schema_history` table, so **redeploys never repeat a migration or delete data**.
- With `MATRIZ_PROFILE=demo`, the seed `V2_1__Demo_data` runs **once**, at the first start: accounts `manager@example.test` (Gestor Demo) and `member@example.test` (Membro Demo), projects "Website redesign" (both) and "Annual report" (Manager only), 6 tasks in the three statuses, assignees. Tasks created during the demo stay.
- Keep `MATRIZ_PROFILE=demo` in this environment. Without it, Flyway's validation fails at start-up because the applied seed migration (V2.1) is no longer on the classpath.
- Changing `MATRIZ_DEMO_PASSWORD` later has no effect (the seed already ran); change the password in the application instead.
- To start over with an empty database: Neon → Branches → reset, or a new database (destructive; only on purpose).

## 6. Public addresses and the demonstration

| What | Address |
|---|---|
| Application | `https://matriz-web.onrender.com` (update if Render gave another name) |
| Backend health | `https://matriz-api.onrender.com/api/actuator/health` |

Demonstration accounts (password: `MATRIZ_DEMO_PASSWORD`, not written anywhere in the repository):

| Account | Shows |
|---|---|
| `manager@example.test` | Dashboard with 2 projects and 6 tasks in every status; Manager actions |
| `member@example.test` | Only "Website redesign": access control (IDOR) |
| Admin (`MATRIZ_ADMIN_EMAIL`) | Account administration and audit log |

**Before the presentation:** open the health address and the application about 2 minutes before, so the backend is awake (see section 7).

## 7. Free plan limitations

- **Spin-down:** a free Render web service stops after 15 minutes without requests; the next request waits about one minute while it starts (Spring Boot start-up plus Neon waking up). The Static Site does not sleep.
- **Sessions end on restart:** sessions and the login rate limit are in memory, so a spin-down, redeploy or restart logs everyone out ("A sessão expirou"). One instance only.
- **750 free instance hours per month** per Render workspace; enough for one service that sleeps when idle.
- **Neon free plan:** the compute suspends when idle (a few seconds to wake up) and storage is limited; see Neon's current plan limits.
- **Email:** free Render services cannot send on ports 25, 465 and 587. Without `MATRIZ_MAIL_*` the reset email is not sent; the rest of the application works (D36). For the demo, an SMTP test inbox on port 2525 (e.g. Mailtrap Email Testing) catches the email without delivering it, like Mailpit; a real provider would need a verified sender domain. Still a pending decision (architecture.md, section 7).
- **Build time:** the backend image builds on Render at every deploy (several minutes); the CI must pass first.

## 8. Validation (after each deploy)

Record the results in [verification.md](verification.md) (rows V-76 to V-80).

1. **Health and HTTPS:** `curl -sSI https://matriz-web.onrender.com` → 200 over HTTPS, headers `X-Frame-Options`, `X-Content-Type-Options`, `Referrer-Policy`; `http://` redirects to `https://`; `/api/actuator/health` through the frontend → `{"status":"UP"}`.
2. **Login with both demo accounts** in the browser; the dashboard shows the data of section 5.
3. **Persistence:** create a project and a task, then **Manual Deploy → Deploy latest commit** on `matriz-api`; after it is live, log in again: both are still there.
4. **IDOR:** as `member@example.test`, open the "Annual report" project by its URL (`/projects/<id>` of the Manager's project) and call `GET /api/projects/<id>` → 404 (D17, openapi.yaml), never the data.
5. **No secrets exposed:** `git grep` and `gitleaks` on the repository → nothing; in the browser, the JavaScript bundle contains no `MATRIZ_*` value and no Neon host; cookies `JSESSIONID` (HttpOnly, Secure) and `XSRF-TOKEN` (Secure) on `matriz-web.onrender.com`.
6. **CORS:** a preflight from another origin is refused: `curl -si -X OPTIONS https://matriz-api.onrender.com/api/auth/login -H 'Origin: https://example.test' -H 'Access-Control-Request-Method: POST'` → 403, no `Access-Control-Allow-Origin`.
7. **Rate limit address (to check):** after a few failed logins, the backend logs and the 429 behaviour should reflect one client, not every user sharing the Render proxy's address.

### If login fails through the rewrite (fallback)

If the health check works but login does not (cookies or POST not passed by the Static Site rewrite), replace `matriz-web` with a Docker web service built from `frontend/Dockerfile` (nginx, as in Compose), with the proxy destination in an environment variable. This needs a small change to `frontend/nginx.conf` and must be approved first. Its cost: a second free service that also sleeps.

## 9. Troubleshooting

| Symptom | Likely cause |
|---|---|
| Backend fails at start with a Flyway validation error about V2.1 | `MATRIZ_PROFILE` is not `demo` |
| `FATAL: password authentication failed` | Wrong owner or app role password; or the role was created on another Neon branch |
| `The server does not support SSL` / SNI errors | Missing `?sslmode=require`; or a pooler host was used |
| First start fails in `V2__app_user_grants.sql`: role `matriz_app` does not exist | Create the role (section 3.1, step 3) before the first deploy, then deploy again |
| Backend killed or restarting (out of memory) | `JAVA_TOOL_OPTIONS` missing |
| Nothing deploys after a push to `main` | CI failed, or no check was reported for that commit ("After CI Checks Pass") |
