# Build stage
FROM maven:3.9.4-eclipse-temurin-11 AS builder
WORKDIR /workspace

# Copy pom.xml first for dependency resolution
COPY pom.xml .
# Copy source
COPY src ./src

# Build the WAR with the cloud profile (includes PostgreSQL driver)
RUN mvn clean package -Pcloud -DskipTests

# Runtime stage
FROM amazoncorretto:11
WORKDIR /opt/payara

# Download Payara Micro
ARG PAYARA_VERSION=6.2025.3
RUN curl -L -o payara-micro.jar \
    https://repo1.maven.org/maven2/fish/payara/distributions/payara-micro/${PAYARA_VERSION}/payara-micro-${PAYARA_VERSION}.jar

# Copy the built WAR, PostgreSQL driver, and post-boot commands
COPY --from=builder /workspace/target/cargo-tracker.war /opt/payara/cargo-tracker.war
COPY --from=builder /workspace/target/postgresql.jar /opt/payara/postgresql.jar
COPY post-boot-commands.asadmin /opt/payara/config/

# Create non-root user
RUN groupadd -r payara && useradd -r -g payara payara \
    && chown -R payara:payara /opt/payara
USER payara

# Environment variables
ENV TZ=UTC
ENV JAVA_OPTS="-Xmx512m -Xms256m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

# Expose the application port
EXPOSE 8080

# Run Payara Micro in the foreground with the WAR deployed
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar payara-micro.jar --deploy cargo-tracker.war --addlibs postgresql.jar --postbootcommands config/post-boot-commands.asadmin"]
