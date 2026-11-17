# Matriz backend image (Worksheet 05). Configuration comes only from environment variables at run time
# (see .env.example and compose.yaml); no credentials are built into the image.

# --- Build stage: Java 21 JDK and the Maven Wrapper ---
# Official Temurin images, Ubuntu 22.04 LTS (jammy) variants
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /app
# Dependencies first, so this layer is reused while only the source code changes
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN ./mvnw -B -q dependency:go-offline
COPY src src
# Tests run in CI (unit tests and Testcontainers integration tests), not inside the image build
RUN ./mvnw -B -q package -DskipTests \
    && cp target/matriz-backend-*.jar app.jar

# --- Runtime stage: Java 21 JRE only ---
FROM eclipse-temurin:21-jre-jammy
# curl only for the health check below
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
RUN useradd --system --uid 10001 --no-create-home matriz
WORKDIR /app
COPY --from=build /app/app.jar app.jar
USER matriz
EXPOSE 8080
# 200 when UP, 503 when DOWN (includes the database connection); management.endpoint.health in application.yml
HEALTHCHECK --interval=10s --timeout=5s --start-period=60s --retries=5 \
    CMD curl -fsS http://localhost:8080/api/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
