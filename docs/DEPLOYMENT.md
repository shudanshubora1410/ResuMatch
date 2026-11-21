# Deploying ResuMatch

Three ways to run it. Pick one.

| Path | Database | Best for |
|---|---|---|
| **A. Render + Aiven MySQL** (Docker) | Aiven MySQL, data persists | A public demo that keeps its data |
| **B. Docker, zero setup** (Render, Railway, Koyeb, Fly.io, VPS) | H2, resets on redeploy | A quick demo with auto-seeded data |
| **C. Plain JAR** | H2 or local MySQL | Local development, viva, no accounts needed |

---

## A. Render (Docker) + Aiven MySQL

Two services, one repo. `render.yaml` in the repo root already describes the Render side.

### 1. Create the Aiven MySQL database

1. Sign in at [app.aiven.io](https://app.aiven.io) → **Create service** → **MySQL**
2. Plan: **Free**. Region: as close as possible to your Render region (Render is set to Singapore in `render.yaml`).
3. Wait for the status to become **Running**, then open the **Connect** tab and note the host, port, user and password.
4. Create the database and tables by running the schema file **once**:

```bash
mysql -h HOST -P PORT -u USER -p --ssl-mode=REQUIRED < schema-mysql.sql
```

The schema file creates the `resumatch` database and all tables. This step is required: with the `mysql` profile the app runs Hibernate in `validate` mode, so it checks the schema but never creates it. Skipping this step makes boot fail with `Unknown database 'resumatch'` or a schema validation error.

### 2. Create the Render web service

1. Render Dashboard → **New → Web Service** → connect your fork of this repo. Render reads `render.yaml`, so the runtime is **Docker**, the health check is `/api/health`, and the profile is `mysql`.
2. In the Render dashboard, add the database values. **Never commit these.**

| Env var | Value |
|---|---|
| `DB_HOST` | Aiven host (no port) |
| `DB_PORT` | Aiven port (often not 3306) |
| `DB_NAME` | `resumatch` |
| `DB_USER` | Aiven user (usually `avnadmin`) |
| `DB_PASS` | Aiven password |

`SPRING_PROFILES_ACTIVE=mysql` and `RESUMATCH_AUTO_SEED=true` come from `render.yaml`.

3. Deploy. A healthy boot log contains:

```
[entrypoint] Truststore ready at /tmp/aiven-truststore.jks
[entrypoint] Using TLS-trusting JDBC URL
The following 1 profile is active: "mysql"
Started ResuMatchApplication in X seconds
```

### How TLS works here

Aiven requires TLS and signs its server certificate with a per-project CA that Java does not trust by default. The `Dockerfile` bundles `aiven-project-ca.pem`, and `docker-entrypoint.sh` builds a truststore from it at container start and builds a TLS-enabled `DB_URL` automatically. You do **not** need to disable TLS on Aiven or set `DB_URL` yourself.

If Aiven rotates the CA, upload the new certificate as a Render **Secret File** and set `AIVEN_CA_FILE=/etc/secrets/aiven-project-ca.pem`.

### Verify it is live

```bash
curl https://YOUR-APP.onrender.com/api/health
# {"status":"UP", ...}

curl https://YOUR-APP.onrender.com/api/public/needs-seed
# {"empty":false, ...}   <- demo data present
```

---

## B. Docker, zero setup (H2)

No database to create. Data lives on the container disk and resets on every redeploy or spin-down. `RESUMATCH_AUTO_SEED=true` repopulates the demo data on the next boot, so the public URL is never blank.

### Render (free tier)

1. **New → Web Service** → connect the repo, runtime **Docker**
2. Health Check Path: `/api/health`
3. Env vars: `SPRING_PROFILES_ACTIVE=h2` and `RESUMATCH_AUTO_SEED=true`

If you use `render.yaml`, change `SPRING_PROFILES_ACTIVE` in the dashboard from `mysql` to `h2`.

### Railway

1. **New Project → Deploy from GitHub repo**. Railway detects the `Dockerfile` and the app reads `$PORT`.
2. Add `SPRING_PROFILES_ACTIVE=h2` and `RESUMATCH_AUTO_SEED=true`.

### Test the image locally

```bash
docker build -t resumatch .
docker run -p 8080:8080 -e SPRING_PROFILES_ACTIVE=h2 -e RESUMATCH_AUTO_SEED=true resumatch
# open http://localhost:8080
```

With a local MySQL instead (create the schema first with `schema-mysql.sql`):

```bash
docker run -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=mysql \
  -e DB_HOST=host.docker.internal \
  -e DB_PORT=3306 \
  -e DB_NAME=resumatch \
  -e DB_USER=root \
  -e DB_PASS=<your-local-password> \
  resumatch
```

---

## C. Plain JAR

No Docker, no account, no credit card.

```bash
mvn -DskipTests package
java -jar target/resumatch.jar
# open http://localhost:8080
```

On Windows run `.\run.bat`. On Mac/Linux run `./run.sh`. Both pick MySQL or H2 automatically.

---

## Free-tier caveats

| Platform | What to expect |
|---|---|
| Render free | Sleeps after 15 min without traffic. The first request after that takes about a minute. The **Starter** plan avoids this, or ping `/api/health` every 10 minutes from a free service such as cron-job.org or UptimeRobot. |
| Aiven free MySQL | Aggressively closes idle connections. The Hikari pool is tuned to validate and keep connections warm; if the first request after a long idle gap fails, retry it. |
| Railway free | Trial credits only, then a card is required. |
| Koyeb free | One free instance, scale-to-zero. |

On Render free, an H2 database (path B) is wiped on every redeploy. Use path A if you need data to persist.

---

## Troubleshooting

**`502 Bad Gateway` on Render**
- The health check path must be exactly `/api/health`. It touches no database and answers in milliseconds. The old `/api/public/needs-seed` path queries JPA, and under lazy initialization the first call bootstrapped Hibernate inside the health check and timed out.
- Check the logs for `Started ResuMatchApplication`. If the app never boots, the container may be out of memory (free tier is 512 MB; the heap is capped at 70% of container RAM).

**`Unknown database 'resumatch'` or a schema validation error**
- You have not run `schema-mysql.sql` against the target database yet (see A.1).

**`Communications link failure` / `PKIX path building failed`**
- The TLS truststore was not built. Look for `[entrypoint] Truststore ready` in the boot log. If you see `WARNING: could not build truststore`, check that `aiven-project-ca.pem` is in the image, or set `AIVEN_CA_FILE` to a Secret File path.
- Using a different MySQL host? Set `DB_URL` yourself (see the comment block in `application-mysql.properties`).

**`Access denied for user`**
- Re-copy `DB_USER` / `DB_PASS` from the Aiven **Connect** tab.

**Boot works but the pages are empty**
- Open `/api/public/needs-seed`. If it returns `empty: true`, the auto-seed did not run: confirm `RESUMATCH_AUTO_SEED=true` is set.

**Duplicate key error on `skill_aliases`**
- Fixed by the `SkillSeed` dedup in `TextNormalizer.skillKey`. Make sure you are running the latest build.

**Port binding error**
- The app binds `0.0.0.0:${PORT}`, never only `localhost`. The `Dockerfile` and entrypoint already handle this.

---

## Demo accounts

Created when `RESUMATCH_AUTO_SEED=true` and the database is empty.

| Role | Email | Password |
|---|---|---|
| Seeker | `priya@demo.com` | `Demo123pass` |
| Recruiter | `riya@technova.com` | `Demo123pass` |
| Admin | `admin@resumatch.local` | `Admin@123` |

The admin account is always created by `AdminSeed`, whether or not auto-seed is on. These credentials are for demos only. Change the admin password before any real use.

---

## Production checklist

- [ ] `SPRING_PROFILES_ACTIVE=mysql` with a managed database
- [ ] Set `RESUMATCH_AUTO_SEED=false` so demo data is not created
- [ ] Change the default admin password
- [ ] Put TLS (nginx or a cloud load balancer) in front of the app
- [ ] Set `GEMINI_API_KEY` if you want AI-written descriptions
- [ ] Configure real SMTP credentials (email is logged to the console by default)
- [ ] Add a database-pinging readiness endpoint next to `/api/health` (liveness)

Demo data already in the database stays after you turn auto-seed off. Remove it from the admin panel with **Reset demo data**.
