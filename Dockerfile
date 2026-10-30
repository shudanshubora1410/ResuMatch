# ResuMatch - container image for Render / Railway / Koyeb / Docker.
#
# Render has no native Java runtime, so a Docker image is the way to deploy
# a Spring Boot app there. Two stages: build the fat JAR with Maven, then
# ship it on a slim JRE.
#
# Stage 1 caches the Maven dependency layer separately from the source, so
# a source-only change rebuilds in ~1 minute instead of re-downloading
# ~150MB of dependencies every time.

# ---------- Stage 1: build ----------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Dependencies first - this layer is cached unless pom.xml changes.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

# Then the source.
COPY src ./src
RUN mvn -B -q -DskipTests package

# ---------- Stage 2: runtime ----------
FROM eclipse-temurin:17-jre
WORKDIR /app

# Run as a non-root user. Render is fine with this and it is best practice.
RUN useradd -r -u 10001 springuser && chown -R springuser:springuser /app
USER springuser

COPY --from=build --chown=springuser:springuser /app/target/resumatch.jar app.jar

# Aiven's CA certificate, so the app can verify the MySQL server's TLS
# certificate. The entrypoint builds a JVM truststore from it at start.
# If Aiven ever rotates the CA, replace this file (or mount the new one
# through Render Secret Files and set AIVEN_CA_FILE).
COPY --chown=springuser:springuser aiven-project-ca.pem /app/aiven-project-ca.pem

# Entrypoint: builds the truststore, then starts the app.
COPY --chown=springuser:springuser docker-entrypoint.sh /app/docker-entrypoint.sh
RUN chmod +x /app/docker-entrypoint.sh

# Render/Railway/Koyeb set PORT automatically. Default to 8080 for local runs.
ENV PORT=8080 \
    AIVEN_CA_FILE=/app/aiven-project-ca.pem \
    AIVEN_TRUSTSTORE=/tmp/aiven-truststore.jks \
    JAVA_TOOL_OPTIONS="-XX:+UseG1GC -XX:MaxRAMPercentage=70 -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -Djava.security.egd=file:/dev/./urandom"

EXPOSE 8080

# Healthcheck hits /api/health, which touches NO database and answers in
# milliseconds. It is deliberately NOT /api/public/needs-seed - that endpoint
# queries JPA repositories, and the first call triggers the full Hibernate
# bootstrap inside the healthcheck request, which times out and makes the
# platform report 502 Bad Gateway.
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -qO- http://127.0.0.1:${PORT}/api/health || exit 1

ENTRYPOINT ["/app/docker-entrypoint.sh"]
