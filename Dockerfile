# =============================================================================
# Multi-stage Dockerfile for Eclipse Cargo Tracker (Jakarta EE / Payara Micro)
# Java 11 | Maven | WAR packaging | Payara Micro runtime
# =============================================================================

# -----------------------------------------------------------------------------
# Stage 1: Builder - compile and package the WAR
# -----------------------------------------------------------------------------
FROM maven:3.9.4-eclipse-temurin-11 AS builder

WORKDIR /workspace

# Copy the entire project (single-module Maven project)
# Copy pom.xml first to leverage Docker layer caching for dependencies
COPY pom.xml .

# Download all dependencies offline (cache layer)
RUN mvn dependency:go-offline -B -q

# Copy the rest of the source code
COPY src ./src
COPY post-boot-commands.asadmin .

# Build the WAR using the cloud profile (PostgreSQL-ready) skipping tests
# Using system mvn - NEVER use mvnw wrapper
RUN mvn clean package -Pcloud -DskipTests -B

# -----------------------------------------------------------------------------
# Stage 2: Runtime - Payara Micro with the packaged WAR
# Explicit base image provided: eclipse-temurin:11-jdk
# Using Payara Micro as the Jakarta EE runtime embedded in the JDK image
# -----------------------------------------------------------------------------
FROM eclipse-temurin:11-jdk

# Metadata labels
LABEL maintainer="Eclipse Cargo Tracker" \
      description="Eclipse Cargo Tracker - Jakarta EE DDD Reference Application" \
      version="3.1-SNAPSHOT" \
      java.version="11" \
      app.port="8080"

# Install Payara Micro JAR
ARG PAYARA_VERSION=6.2025.3
RUN apt-get update && apt-get install -y --no-install-recommends \
        ca-certificates \
    && rm -rf /var/lib/apt/lists/*

# Download Payara Micro
ADD https://repo1.maven.org/maven2/fish/payara/extras/payara-micro/${PAYARA_VERSION}/payara-micro-${PAYARA_VERSION}.jar /opt/payara/payara-micro.jar

# Create non-root user for security
RUN groupadd -r cargotracker && useradd -r -g cargotracker -d /app -s /sbin/nologin cargotracker

# Set working directory
WORKDIR /app

# Copy the built WAR from builder stage
COPY --from=builder /workspace/target/cargo-tracker.war /app/cargo-tracker.war

# Copy Payara post-boot commands (JNDI, datasource, deploy)
COPY --from=builder /workspace/post-boot-commands.asadmin /app/post-boot-commands.asadmin

# Create data directory for H2 file database (used in non-cloud mode)
RUN mkdir -p /app/cargo-tracker-data && chown -R cargotracker:cargotracker /app

# Set timezone
ENV TZ=UTC

# JVM options optimized for containers
ENV JAVA_OPTS="-Xmx512m -Xms256m \
    -XX:+UseContainerSupport \
    -XX:MaxRAMPercentage=75.0 \
    -XX:+UnlockExperimentalVMOptions \
    -Djava.net.preferIPv4Stack=true \
    -Dfile.encoding=UTF-8 \
    -Duser.timezone=UTC"

# Application environment variables (override at runtime)
ENV PAYARA_VERSION=${PAYARA_VERSION}
ENV DB_DRIVER_CLASS=org.h2.jdbcx.JdbcDataSource
ENV DB_JDBC_URL=jdbc:h2:file:/app/cargo-tracker-data/cargo-tracker-database
ENV DB_USER=
ENV DB_PASSWORD=
ENV GRAPH_TRAVERSAL_URL=http://localhost:8080/cargo-tracker/rest/graph-traversal/shortest-path

# Expose application port
EXPOSE 8080

# Switch to non-root user
USER cargotracker

# Use exec form for proper signal handling (graceful shutdown)
ENTRYPOINT ["sh", "-c", \
    "exec java $JAVA_OPTS \
    -jar /opt/payara/payara-micro.jar \
    --deploy /app/cargo-tracker.war \
    --contextroot /cargo-tracker \
    --port 8080 \
    --postbootcommandfile /app/post-boot-commands.asadmin \
    --nocluster"]
