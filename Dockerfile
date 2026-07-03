# Stage 1: Build stage
FROM maven:3.9.4-eclipse-temurin-11 AS builder
WORKDIR /workspace

# Copy pom.xml first for dependency caching
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code and build the application
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Runtime stage
FROM amazoncorretto:11
WORKDIR /app

# Create a non-root user for security
RUN groupadd -r appuser && useradd -r -g appuser appuser

# Copy the WAR file from the builder stage
COPY --from=builder /workspace/target/cargo-tracker.war /app/cargo-tracker.war

# Set JVM memory settings and container awareness
ENV JAVA_OPTS="-Xmx512m -Xms256m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"
ENV TZ=UTC

# Expose the application port
EXPOSE 8080

# Use a runtime that can execute the WAR (assuming Payara Micro or similar for Jakarta EE 10)
# Since the project is a WAR, it needs a servlet container. 
# We'll use Payara Micro as it's mentioned in the pom.xml profiles.
RUN mvn dependency:get -Dartifact=fish.payara.extras:payara-micro:6.2025.3 -Ddest=/app/payara-micro.jar

USER appuser

# Start the application using Payara Micro
ENTRYPOINT ["java", "-Xmx512m", "-Xms256m", "-XX:+UseContainerSupport", "-jar", "/app/payara-micro.jar", "--deploy", "/app/cargo-tracker.war"]
