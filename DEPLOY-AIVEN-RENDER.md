# Deploy ResuMatch — Aiven (MySQL) + Render (app)

Same stack as Apna Samaan. Two services, one repo.

---

## Part 1 — Aiven MySQL database

1. Sign in at [app.aiven.io](https://app.aiven.io)
2. **Create a service** → type **MySQL**
3. Plan: **Free** (`hobbyist` — 1 CPU, 1 GB RAM, 5 GB storage)
4. Region: pick the one closest to your Render region (**Mumbai / Singapore** for least latency)
5. **Create service**, then wait for status = **Running**
6. Open the service → **Connect** tab → show **Service URI**

You'll get something like:
```
mysql://YOUR_USER:YOUR_PASSWORD@YOUR_HOST:PORT/defaultdb
```

7. Open **Data** tab → connect with a MySQL client and create the schema:

```sql
CREATE DATABASE resumatch CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

> **Why you must create this manually:** Hibernate's `ddl-auto=update` creates *tables*,
> but it will not create the *database* itself. If the database doesn't exist, boot fails
> with `Unknown database 'resumatch'`.

### Extract the individual values

From the Aiven **Connect** tab, split the URI:

| Render env var | Aiven value |
|---|---|
| `DB_HOST` | host from the URI (no port) |
| `DB_PORT` | port from the URI (usually `3306` — Aiven may use a custom port) |
| `DB_NAME` | `resumatch` |
| `DB_USER` | username from the URI |
| `DB_PASS` | password from the URI |

> ⚠️ **Aiven requires TLS** by default. The JDBC URL in `application-mysql.properties`
> uses `useSSL=false`. If the connection is refused, switch to Aiven's **Connection
> Security** settings and either disable "TLS required" or add `useSSL=true&requireSSL=true`
> to the URL. See "Troubleshooting" below.

---

## Part 2 — Render web service

1. Push this repo to GitHub (see the README)
2. Render Dashboard → **New → Web Service** → connect `github.com/shudanshubora1410/ResuMatch`
3. Settings:

| Field | Value |
|---|---|
| Runtime | `Java` |
| Build Command | `mvn -B -DskipTests package` |
| Start Command | `java -Dserver.port=$PORT -Dserver.address=0.0.0.0 -XX:+UseG1GC -XX:MaxRAMPercentage=70 -jar target/resumatch.jar` |
| Instance Type | `Free` (or `Starter` $7/mo for no spin-down) |
| Health Check Path | `/api/health` |
| Region | same as your Aiven region |

> **Why native Java and not Docker?** Render caches the Maven repository between
> builds, so the first build downloads dependencies once and every build after
> that is ~1 minute. A Docker build runs Maven inside a container from scratch
> every time (5-8 min) and can hit the free-tier build timeout. The `Dockerfile`
> is still in the repo for VPS / Azure / Fly.io deployments.

4. **Environment Variables:**

```
SPRING_PROFILES_ACTIVE = mysql
DB_HOST                = <from Aiven>
DB_PORT                = <from Aiven>
DB_NAME                = resumatch
DB_USER                = <from Aiven>
DB_PASS                = <from Aiven>
RESUMATCH_AUTO_SEED    = true
```

5. **Deploy.** Watch the logs — you want to see:

```
The following 1 profile is active: "mysql"
HikariPool-1 - Added connection com.mysql.cj.jdbc.ConnectionImpl@...
Started ResuMatchApplication in X seconds
[DemoAutoSeed] Demo data seeded successfully.
```

6. Your URL: `https://resumatch-xxxx.onrender.com`

---

## Part 3 — Verify it's live

```bash
# Health
curl https://resumatch-xxxx.onrender.com/api/health
# {"status":"UP","app":"ResuMatch","port":10000}

# Demo data present?
curl https://resumatch-xxxx.onrender.com/api/public/needs-seed
# {"empty":false,"seekers":3,"jobs":6,"applications":14}
```

Then open the URL in a browser and log in as `priya@demo.com` / `Demo123pass`.

---

## 🔴 Free-tier caveats (read this)

| Issue | What happens | Fix |
|---|---|---|
| **Spin-down** | After 15 min with no traffic, Render sleeps the service. The next request shows a "loading" page for ~60s while the JVM restarts. | Use the **Starter** plan ($7/mo), or ping the URL every 10 min from a cron job. |
| **Cold start on first load** | The first visitor after a long idle waits ~1 min. | Same as above. |
| **H2 would reset** | You're using MySQL, so **data persists**. | ✅ Nothing to do. |
| **Auto-seed re-runs** | `RESUMATCH_AUTO_SEED=true` only seeds when the DB is empty, so it won't wipe your data. | ✅ Safe to leave on. |
| **Aiven free tier sleeps** | Aiven's free MySQL also scales down after a while and may drop the connection. | The app reconnects automatically (HikariCP). If the first request after a long gap fails, retry. |

---

## 🔧 Troubleshooting

### `Unknown database 'resumatch'`
You didn't create the schema in Aiven. Go to Aiven → **Data** tab → connect → run the `CREATE DATABASE` statement.

### `Communications link failure` / `Connection refused`
**Aiven + TLS.** Aiven requires TLS for connections. Two options:

**Option A (simplest):** Aiven → your service → **Settings** → **Connection Security** → turn off "Require TLS" / set the minimum TLS version to `TLSv1.2` with the "disable" toggle if available. Then `useSSL=false` works.

**Option B (keep TLS on):** edit `src/main/resources/application-mysql.properties`:

```properties
spring.datasource.url=jdbc:mysql://${DB_HOST:localhost}:${DB_PORT:3306}/${DB_NAME:resumatch}?useSSL=true&requireSSL=true&serverTimezone=UTC&allowPublicKeyRetrieval=true
```

### `Access denied for user`
Check `DB_USER` / `DB_PASS`. Aiven usernames look like `avnadmin`. Make sure you copied the password from the Connect tab, not the login page.

### `Public Key Retrieval is not allowed`
Already handled — the URL includes `allowPublicKeyRetrieval=true`.

### Boot succeeds but pages are empty
Hit `/api/public/needs-seed`. If `empty: true`, the auto-seed didn't run. Check that `RESUMATCH_AUTO_SEED=true` is set in Render's env vars and that the logs show `[DemoAutoSeed]`.

### 502 Bad Gateway
Health check path must be exactly `/api/health` — **not** `/api/public/needs-seed`. The latter queries JPA repositories and the first call triggers the full Hibernate bootstrap inside the healthcheck request, which times out. (This was the original bug — see the README.)

### `ddl-auto=update` created some tables but the app crashes on one
Check the Render logs for the exact DDL error. On MySQL 8 the only schema incompatibility we had was `CLOB`, which is fixed — all LOB columns are now `LONGTEXT`.

---

## 💡 Optional: keep it awake for free

If you want the free tier to stay warm, use a free cron service (cron-job.org, UptimeRobot) that pings `/api/health` every 10 minutes:

```
GET https://resumatch-xxxx.onrender.com/api/health
```

That prevents the spin-down and keeps cold starts at ~2 seconds.

---

## 🔐 If you ever want to remove auto-seed

Set `RESUMATCH_AUTO_SEED=false` once real users start registering. The admin account is created regardless by `AdminSeed`. The demo data (Priya, Riya, the 6 jobs) stays in the database either way — remove it from the admin panel with **Reset demo data**.
