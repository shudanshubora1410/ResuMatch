# Deploying ResuMatch

Two supported paths. Pick one.

---

## Option A — Docker (anywhere: Render, Railway, Koyeb, Fly.io, a VPS)

### Render (free tier)

1. Push the repo to GitHub
2. Render Dashboard → **New → Web Service** → connect `github.com/shudanshubora1410/ResuMatch`
3. Settings:
   - **Runtime**: `Docker`
   - **Health Check Path**: `/api/health`
   - **Instance Type**: `Free`
4. Environment Variables:
   - `SPRING_PROFILES_ACTIVE` = `h2`
   - `RESUMATCH_AUTO_SEED` = `true`
5. Deploy. You get a URL like `https://resumatch-xyz.onrender.com`

**Why it works now (it didn't before):** the old healthcheck path was `/api/public/needs-seed`, which queries JPA repositories. Under `spring.main.lazy-initialization=true` the first call to it triggered the entire Hibernate bootstrap *inside the healthcheck request*, which timed out and made Render return 502 Bad Gateway. `/api/health` touches no database and answers in ~3ms.

### Railway

1. Railway → **New Project → Deploy from GitHub repo**
2. Railway auto-detects the Dockerfile
3. Add `SPRING_PROFILES_ACTIVE=h2` and `RESUMATCH_AUTO_SEED=true`
4. Railway assigns a port automatically; the Dockerfile reads `$PORT`

### Local Docker test

```bash
docker build -t resumatch .
docker run -p 8080:8080 -e RESUMATCH_AUTO_SEED=true resumatch
# open http://localhost:8080
```

### Local Docker test with MySQL

```bash
docker run -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=mysql \
  -e DB_HOST=host.docker.internal \
  -e DB_PORT=3306 \
  -e DB_NAME=resumatch \
  -e DB_USER=root \
  -e DB_PASS=Root@1234 \
  resumatch
```

---

## Option B — No Docker: run the JAR directly

This is the path that always works. No account, no credit card, no platform limits.

```bash
mvn -DskipTests package
java -jar target/resumatch.jar
# open http://localhost:8080
```

Or on Windows, just double-click / run `.\run.bat` — it auto-detects MySQL vs H2 and opens on port 8090.

---

## ⚠️ Free-tier limitations you should know about

| Platform | Limitation |
|---|---|
| Render free | Spins down after 15 min of no traffic. First request after that takes ~1 min (cold start). Data resets on every redeploy (H2 file is on the container disk). |
| Railway free | Trial credits only, then requires a card. |
| Koyeb free | One free instance, scale-to-zero. |

This is why `RESUMATCH_AUTO_SEED=true` matters — after every redeploy or spin-down the database is empty, and the auto-seeder repopulates it on the next boot. Without it, the public URL shows a blank app.

---

## 🔧 Troubleshooting

### "502 Bad Gateway" on Render
- Healthcheck path must be exactly `/api/health`
- Check the Render logs for `Started ResuMatchApplication in X seconds` — the app should boot in ~7s
- If the app never boots, the container is OOMing. Free tier is 512MB RAM. The Dockerfile caps the heap at 70% of container RAM via `-XX:MaxRAMPercentage=70`.

### App boots but the page is empty
- Hit `https://your-url/api/health` — should return `{"status":"UP",...}`
- Hit `https://your-url/api/public/needs-seed` — if `empty: true`, the auto-seed didn't run. Check that `RESUMATCH_AUTO_SEED=true` is set.

### "Application run failed" with a duplicate key error
- The `skill_aliases` unique index. The `SkillSeed` dedup fix (in `TextNormalizer.skillKey` + `SkillSeed`) handles this — make sure you're running the latest JAR.

### Database persists but H2 resets on every deploy
- Expected on free tiers — the container filesystem is ephemeral. Use MySQL/Postgres for persistence.
- On Render free there's no persistent disk. For a real deployment, point `DB_*` at a managed MySQL (Render Postgres / Aiven / PlanetScale).

### Port binding error
- The app binds `0.0.0.0:${PORT}` — never just `localhost`. This is already handled in the Dockerfile and `application.properties`.

---

## 📋 Production checklist (if you ever pay for hosting)

- [ ] Switch `SPRING_PROFILES_ACTIVE` to `mysql`
- [ ] Point `DB_HOST`/`DB_USER`/`DB_PASS` at a managed database
- [ ] Put nginx or a cloud load balancer in front for TLS
- [ ] Set `GEMINI_API_KEY` if you want the AI bullet-rewriter
- [ ] Configure real SMTP credentials (the `EmailService` logs to console by default)
- [ ] Set `resumatch.auto-seed=false` — you don't want demo data in production
- [ ] Add a real health/readiness split: `/api/health` (liveness) vs a DB-pinging readiness endpoint

---

## 🔗 Demo accounts on the deployed instance

| Role | Identifier | Password |
|---|---|---|
| Seeker | `priya@demo.com` | `Demo123pass` |
| Recruiter | `riya@technova.com` | `Demo123pass` |
| Admin | `admin@resumatch.local` | `Admin@123` |

These exist because `RESUMATCH_AUTO_SEED=true` is set. The admin account is always created by `AdminSeed` regardless of that flag.
