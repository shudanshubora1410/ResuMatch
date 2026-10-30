# ResuMatch - container image for cloud deploys (Render, Railway, Koyeb, Docker).
#
# Two stages: build the fat JAR with Maven, then ship it on a slim JRE.
# The runtime image contains ONLY the JRE and the JAR - no Maven, no source.

# ---------- Stage 1: build ----------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Copy pom.xml first so the dependency download layer is cached and
# rebuilds after a source-only change are fast.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q -DskipTests package

# ---------- Stage 2: runtime ----------
FROM eclipse-temurin:17-jre
WORKDIR /app

# Run as a non-root user (Render/Railway best practice; some platforms
# refuse to start a container running as root).
RUN useradd -r -u 10001 springuser && chown -R springuser:springuser /app
USER springuser

COPY --from=build --chown=springuser:springuser /app/target/resumatch.jar app.jar

# Render/Railway/Koyeb set PORT automatically. Default to 8080 for local docker runs.
ENV PORT=8080 \
    JAVA_TOOL_OPTIONS="-XX:+UseG1GC -XX:MaxRAMPercentage=70 -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -Djava.security.egd=file:/dev/./urandom"

EXPOSE 8080

# Healthcheck hits /api/health, which touches NO database and answers in
# milliseconds. It is deliberately NOT /api/public/needs-seed - that endpoint
# queries JPA repositories, and under lazy loading the first call triggers the
# full Hibernate bootstrap inside the healthcheck request, which times out and
# makes the platform report 502 Bad Gateway.
HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 \
    CMD wget -qO- http://127.0.0.1:${PORT}/api/health || exit 1

# Shell form so ${PORT} is expanded at runtime.
# -Dserver.address=0.0.0.0  -> bind all interfaces so the platform LB can reach us
# -Dserver.port=${PORT:-8080} -> the platform-assigned port
ENTRYPOINT ["sh", "-c", "java -jar app.jar --server.port=${PORT:-8080} --server.address=0.0.0.0"]
