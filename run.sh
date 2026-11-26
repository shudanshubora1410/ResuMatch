#!/bin/bash
# ResuMatch quick-run script for macOS / Linux.
#
# Zero-setup by default: uses the embedded H2 file database, so the only
# prerequisites are a JDK and Maven. No MySQL server required.
#
# To use MySQL instead, export DB_HOST before running this script.

# ---- Optional MySQL config (only used when DB_HOST is already set) ----
export DB_PORT="${DB_PORT:-3306}"
export DB_NAME="${DB_NAME:-resumatch}"
export DB_USER="${DB_USER:-root}"

# ---- Auto-detect ----
if [ -n "$DB_HOST" ]; then
    echo "[run.sh] DB_HOST is set -> using MySQL profile ($DB_HOST:$DB_PORT/$DB_NAME)"
    export SPRING_PROFILES_ACTIVE=mysql
else
    echo "[run.sh] DB_HOST not set -> using H2 (local file mode). Zero setup needed."
    export SPRING_PROFILES_ACTIVE=h2
fi

# ---- Seed the demo dataset on first run ----
export RESUMATCH_AUTO_SEED=true

# ---- Make sure the JAR is built ----
if [ ! -f target/resumatch.jar ]; then
    echo "[run.sh] JAR not found. Building now..."
    mvn -q -DskipTests package
    if [ $? -ne 0 ]; then
        echo "[run.sh] Build failed. Run 'mvn package' manually to see the error."
        exit 1
    fi
fi

# ---- Start the app on port 8090 ----
echo "[run.sh] Starting ResuMatch on http://localhost:8090"
echo "[run.sh] Logins: priya@demo.com / Demo123pass    (seeker)"
echo "[run.sh]          riya@technova.com / Demo123pass (recruiter)"
echo "[run.sh]          admin@resumatch.local / Admin@123 (admin)"
echo "[run.sh] Press Ctrl+C to stop."
java -Dserver.port=8090 -jar target/resumatch.jar
