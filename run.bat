@echo off
REM ResuMatch quick-run script for Windows.
REM
REM Zero-setup by default: uses the embedded H2 file database, so the only
REM prerequisites are a JDK and Maven. No MySQL server required.
REM
REM To use MySQL instead, set the environment variable DB_HOST before running
REM this script, e.g.  set DB_HOST=localhost   then run run.bat

REM ---- Optional MySQL config (only used when DB_HOST is already set) ----
if not defined DB_HOST set DB_HOST=
if not defined DB_PORT set DB_PORT=3306
if not defined DB_NAME set DB_NAME=resumatch
if not defined DB_USER set DB_USER=root

REM ---- Pick profile ----
if defined DB_HOST goto use_mysql
echo [run.bat] No DB_HOST set - using H2 profile. Zero setup needed.
set SPRING_PROFILES_ACTIVE=h2
goto start_app
:use_mysql
echo [run.bat] DB_HOST is set - using MySQL profile %DB_HOST%:%DB_PORT%/%DB_NAME%
set SPRING_PROFILES_ACTIVE=mysql
goto start_app

:start_app

REM ---- Seed the demo dataset on first run ----
set RESUMATCH_AUTO_SEED=true

REM ---- Build (or rebuild) the JAR ----
echo [run.bat] Building project with Maven...
call mvn -q -DskipTests package
if errorlevel 1 goto build_failed

REM ---- Start the app on port 8090 ----
echo [run.bat] Starting ResuMatch on http://localhost:8090
echo [run.bat] Logins: priya@demo.com / Demo123pass    (seeker)
echo [run.bat]          riya@technova.com / Demo123pass (recruiter)
echo [run.bat]          admin@resumatch.local / Admin@123 (admin)
echo [run.bat] Press Ctrl+C to stop.
java "-Dserver.port=8090" -jar target\resumatch.jar
goto :eof

:build_failed
echo.
echo [run.bat] Build failed. Re-running with full output for details:
call mvn -DskipTests package
pause
exit /b 1
