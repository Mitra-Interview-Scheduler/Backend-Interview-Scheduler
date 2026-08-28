# Railway dry run — Backend + PostgreSQL (DryRun-01)

Deploy the Spring Boot backend from branch **`DryRun-01`** with PostgreSQL on Railway.  
Production profile reads secrets from **environment variables** (`application-prod.properties`).

## Prerequisites

- [Railway account](https://railway.app) (Hobby / trial credits)
- GitHub repo: `Mitra-Interview-Schaduler/Backend-Interview-Scheduler`
- Branch: **`DryRun-01`** (created from `dev`)
- [Railway CLI](https://docs.railway.app/guides/cli) (optional): `npm i -g @railway/cli`

## 1. Create the Railway project

1. Railway dashboard → **New Project** → **Deploy from GitHub repo**
2. Select **Backend-Interview-Scheduler**
3. Set branch to **`DryRun-01`**
4. Root directory: `/` (repo root — `Dockerfile` and `railway.json` are at root)

## 2. Add PostgreSQL

1. In the same project → **Add service** → **Database** → **PostgreSQL**
2. Wait until Postgres is running
3. Open the **Backend** service → **Variables** → **Add variable reference** (or “Reference”):
   - `SPRING_DATASOURCE_URL` = `jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}`
   - `SPRING_DATASOURCE_USERNAME` = `${{Postgres.PGUSER}}`
   - `SPRING_DATASOURCE_PASSWORD` = `${{Postgres.PGPASSWORD}}`

   Replace `Postgres` with your Postgres service name if different.

Flyway runs on startup and creates the schema in the Railway database.

## 3. Set application secrets

Copy values from your local `application.properties` into Railway **Backend** service variables.

Use [`deploy/railway-dryrun.env.example`](../deploy/railway-dryrun.env.example) as the checklist.  
A filled local file **`deploy/railway-dryrun.env`** (gitignored) can be generated from your current `application.properties`.

### Required (app will not start without these)

| Railway variable | Local `application.properties` key |
|------------------|-------------------------------------|
| `SPRING_DATASOURCE_URL` | Postgres reference (see above) |
| `SPRING_DATASOURCE_USERNAME` | Postgres reference |
| `SPRING_DATASOURCE_PASSWORD` | Postgres reference |
| `JWT_SECRET` | `jwt.secret` |
| `GOOGLE_CLIENT_ID` | `google.client.id` |
| `APP_CORS_ALLOWED_ORIGINS` | `app.cors.allowed-origins` (+ your deployed frontend URL) |

### Recommended (from current local config)

| Railway variable | Local key |
|------------------|-----------|
| `GOOGLE_CLIENT_SECRET` | `google.client.secret` |
| `GOOGLE_CALENDAR_ENCRYPTION_KEY` | `google.calendar.encryption-key` |
| `GOOGLE_WORKSPACE_DOMAIN` | `google.workspace.domain` |
| `GOOGLE_CALENDAR_REQUIRED` | `google.calendar.required` |
| `GOOGLE_RECRUITMENT_SHARED_DRIVE_ID` | `google.recruitment.shared-drive-id` |
| `SPRING_MAIL_HOST` | `spring.mail.host` |
| `SPRING_MAIL_PORT` | `spring.mail.port` |
| `SPRING_MAIL_USERNAME` | `spring.mail.username` |
| `SPRING_MAIL_PASSWORD` | `spring.mail.password` |
| `JWT_ACCESS_EXPIRATION_MS` | `jwt.access-expiration-ms` |
| `AUTH_REFRESH_TOKEN_EXPIRATION_MS` | `auth.refresh-token.expiration-ms` |
| `AUTH_REFRESH_TOKEN_COOKIE_NAME` | `auth.refresh-token.cookie-name` |
| `AUTH_REFRESH_TOKEN_COOKIE_SECURE` | `true` (HTTPS on Railway) |
| `AUTH_REFRESH_TOKEN_COOKIE_SAMESITE` | `Strict` |
| `NOTIFICATION_EMAIL_ENABLED` | `notification.email.enabled` |
| `SERVER_FORWARD_HEADERS_STRATEGY` | `native` |

### URLs to update after first deploy

1. Deploy once; copy the public URL (e.g. `https://backend-production-xxxx.up.railway.app`)
2. Set:
   - `GOOGLE_CALENDAR_REDIRECT_URI` = `https://<your-backend>/api/integrations/google-calendar/callback`
   - Add the same backend origin to Google Cloud OAuth **Authorized redirect URIs**
3. Set `APP_FRONTEND_URL` and `APP_CORS_ALLOWED_ORIGINS` to your frontend URL when you host it

### Google service account (Shared Drive)

Local config uses a **file path** — that does not work on Railway. Use base64 instead:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("D:\Mitra\third-pen-434011-r7-a22265d89714.json"))
```

Set **`GOOGLE_RECRUITMENT_SERVICE_ACCOUNT_KEY_BASE64`** to the output (single line).  
Leave **`GOOGLE_RECRUITMENT_SERVICE_ACCOUNT_KEY_PATH`** empty.

## 4. Deploy settings

Already in repo:

- [`Dockerfile`](../Dockerfile) — Java 21, `SPRING_PROFILES_ACTIVE=prod`
- [`railway.json`](../railway.json) — Docker build, health check `/actuator/health/liveness`

Railway → Backend service → **Settings**:

- **Health check path**: `/actuator/health/liveness`
- **Port**: Railway sets `PORT` automatically

## 5. CLI shortcut (optional)

```powershell
cd Backend-Interview-Scheduler
railway login
railway link          # pick project + Backend service
railway up            # deploy current branch
```

To bulk-set variables from your local gitignored file:

```powershell
.\deploy\set-railway-vars.ps1
```

## 6. Verify

1. **Health**: `GET https://<backend>/actuator/health/liveness` → `{"status":"UP"}`
2. **API**: `GET https://<backend>/api/auth/csrf` → should return CSRF token JSON
3. **Logs**: Railway → Backend → Deployments → View logs (Flyway migrations should succeed)

## 7. Connect frontend (later)

In the frontend `.env`:

```
VITE_API_BASE_URL=https://<your-backend>.up.railway.app/api
```

Ensure that URL is in `APP_CORS_ALLOWED_ORIGINS` on the backend.

## Free tier notes

- Railway Hobby includes limited monthly credits; Postgres + always-on backend consume credits quickly.
- For a short **dry run**, you can scale down or delete services when finished.
- Sleep / cold starts may apply on low credits — first request after idle can be slow.

## Branch workflow

```bash
git checkout dev
git pull origin dev
git checkout DryRun-01
git merge dev   # when you want to refresh dry-run from dev
git push -u origin DryRun-01
```
