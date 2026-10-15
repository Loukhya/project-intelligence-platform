# ===================================================================
# Multi-stage Dockerfile for DPIP Spring Boot Application
# ===================================================================

# Stage 1: Build the Application
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app

# Cache Maven dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code and build
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Minimal Runtime Environment
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Create a non-root application user for container security
RUN groupadd -r dpip && useradd -r -g dpip dpip
USER dpip

# Copy executable jar from build stage
COPY --from=build --chown=dpip:dpip /app/target/platform-0.0.1-SNAPSHOT.jar app.jar

# Expose default HTTP port
EXPOSE 8080

# Environment variables with sensible defaults
ENV SPRING_PROFILES_ACTIVE=local \
    JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseG1GC"

# Healthcheck
HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
