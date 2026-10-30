@echo off
REM ResuMatch quick-run script for Windows.
REM
REM Auto-detects MySQL vs H2:
REM   - if DB_HOST is set, uses the MySQL profile (set DB_* below)
REM   - otherwise falls back to H2 in file mode (zero setup)
REM
REM Always runs mvn package; Maven is incremental so unchanged sources
REM rebuild in a few seconds.

REM ---- MySQL config (edit these to match your local MySQL) ----
set DB_HOST=localhost
set DB_PORT=3306
set DB_NAME=resumatch
set DB_USER=root
set DB_PASS=Root@1234

REM ---- Pick profile based on DB_HOST ----
if defined DB_HOST goto use_mysql
echo [run.bat] DB_HOST not set - using H2 profile
set SPRING_PROFILES_ACTIVE=h2
goto start_app
:use_mysql
echo [run.bat] DB_HOST is set - using MySQL profile %DB_HOST%:%DB_PORT%/%DB_NAME%
set SPRING_PROFILES_ACTIVE=mysql
goto start_app

:start_app

REM ---- Build (or rebuild) the JAR ----
echo [run.bat] Building project with Maven...
call mvn -q -DskipTests package
if errorlevel 1 goto build_failed

REM ---- Start the app on port 8090 ----
echo [run.bat] Starting ResuMatch on http://localhost:8090
java "-Dserver.port=8090" -jar target\resumatch.jar
goto :eof

:build_failed
echo.
echo [run.bat] Build failed. Re-running with full output for details:
call mvn -DskipTests package
pause
exit /b 1
