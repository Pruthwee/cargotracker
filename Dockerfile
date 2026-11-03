# Build stage
FROM maven:3.9.4-eclipse-temurin-11 AS builder

WORKDIR /workspace

# Copy pom.xml and resolve dependencies first for better caching
COPY pom.xml .
RUN mvn dependency:go-offline -Pcloud

# Copy source code and build
COPY src ./src
RUN mvn clean package -Pcloud -DskipTests

# Runtime stage
FROM amazoncorretto:11

LABEL maintainer="Eclipse Cargo Tracker"

# Create a non-root user
RUN groupadd -r -g 10001 cargotracker && useradd -r -u 10001 -g cargotracker cargotracker

# Copy Payara Micro, PostgreSQL driver, and the WAR from the build stage
COPY --from=builder /workspace/target/payara-micro.jar /opt/cargo-tracker/payara-micro.jar
COPY --from=builder /workspace/target/postgresql.jar /opt/cargo-tracker/postgresql.jar
COPY --from=builder /workspace/target/cargo-tracker.war /opt/cargo-tracker/cargo-tracker.war

WORKDIR /opt/cargo-tracker

# Expose the application port
EXPOSE 8080

# JVM settings for container
ENV JAVA_OPTS="-Xmx512m -Xms256m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"
ENV TZ=UTC
ENV JAVA_TOOL_OPTIONS="-Dfile.encoding=UTF-8 -Duser.timezone=UTC"

# Environment variables for externalized configuration
# Payara resolves ${ENV=...} placeholders in web.xml at runtime
# DB_URL, DB_USER, DB_PASSWORD must be set at runtime via environment variables
# GRAPH_TRAVERSAL_URL defaults to the service's own REST endpoint
ENV GRAPH_TRAVERSAL_URL="http://localhost:8080/rest/graph-traversal/shortest-path"

# Switch to non-root user
USER 10001

# Start Payara Micro with the WAR deployed
# Payara Micro resolves ${ENV=...} placeholders in web.xml at runtime
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS $JAVA_TOOL_OPTIONS -jar payara-micro.jar --deploy cargo-tracker.war --port 8080 --contextRoot / --addlibs postgresql.jar"]
