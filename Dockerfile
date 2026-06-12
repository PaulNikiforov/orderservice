# ── Build stage ──────────────────────────────────────────────────────────────
FROM maven:3.9.9-eclipse-temurin-21-alpine AS build
WORKDIR /app

# Fetch dependencies separately — layer is cached as long as pom.xml doesn't change
COPY pom.xml .
RUN mvn dependency:go-offline -q

COPY src ./src
# maven.test.skip skips BOTH test compilation and execution (faster than -DskipTests,
# which still compiles the test sources + their dependencies)
RUN mvn package -Dmaven.test.skip=true -q

# ── Runtime stage ─────────────────────────────────────────────────────────────
FROM eclipse-temurin:21.0.5_11-jre-alpine
WORKDIR /app

# Non-root user for security
RUN addgroup -S app && adduser -S app -G app
USER app

# --chown so the jar is owned by the non-root runtime user (COPY ignores USER otherwise)
COPY --from=build --chown=app:app /app/target/orderservice-*.jar app.jar

EXPOSE 8082

# Container-aware heap sizing; override at runtime with -e JAVA_OPTS="..."
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"

# busybox wget (bundled in alpine) + grep against the actuator health endpoint.
# start-period covers Spring context + Liquibase migrations on first boot.
HEALTHCHECK --interval=15s --timeout=5s --start-period=40s --retries=5 \
  CMD wget -q -O - http://localhost:8082/actuator/health | grep -q '"status":"UP"' || exit 1

# exec → java becomes PID 1 and receives SIGTERM directly for graceful shutdown
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
