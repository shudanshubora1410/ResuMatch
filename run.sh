#!/bin/bash
# ResuMatch quick-run script for macOS / Linux.
#
# Auto-detects whether to use MySQL (if DB_HOST is set) or fall back to
# H2 (works out of the box, no setup).

# ---- MySQL config (edit these to match your local MySQL) ----
export DB_HOST=localhost
export DB_PORT=3306
export DB_NAME=resumatch
export DB_USER=root
export DB_PASS=Root@1234

# ---- Auto-detect ----
if [ -n "$DB_HOST" ]; then
    echo "[run.sh] DB_HOST is set -> using MySQL profile ($DB_HOST:$DB_PORT/$DB_NAME)"
    export SPRING_PROFILES_ACTIVE=mysql
else
    echo "[run.sh] DB_HOST not set -> using H2 (local file mode)"
    export SPRING_PROFILES_ACTIVE=h2
fi

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
java -Dserver.port=8090 -jar target/resumatch.jar
