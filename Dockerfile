FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn package -Dmaven.test.skip=true -B

FROM eclipse-temurin:21-jre-alpine AS extractor
WORKDIR /app
COPY --from=builder /app/target/orderservice-*.jar app.jar
RUN java -Djarmode=layertools -jar app.jar extract --destination extracted

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S app && adduser -S app -G app

COPY --chown=app:app --from=extractor /app/extracted/dependencies/ ./
COPY --chown=app:app --from=extractor /app/extracted/spring-boot-loader/ ./
COPY --chown=app:app --from=extractor /app/extracted/snapshot-dependencies/ ./
COPY --chown=app:app --from=extractor /app/extracted/application/ ./

USER app

EXPOSE 8082

HEALTHCHECK --interval=15s --timeout=5s --start-period=40s --retries=5 \
  CMD wget -q -O - http://localhost:8082/actuator/health | grep -q '"status":"UP"' || exit 1

ENV SPRING_PROFILES_ACTIVE=docker

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", \
            "org.springframework.boot.loader.launch.JarLauncher"]
