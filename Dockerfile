# Stage 1: Build stage
FROM maven:3.9.4-eclipse-temurin-11 AS builder
WORKDIR /workspace

# Copy pom.xml first to leverage Docker layer caching for dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy the rest of the source code
COPY . .

# Build the application WAR file
# Using the 'payara' profile as it's the default and provides a standard Jakarta EE environment
RUN mvn clean package -Ppayara -DskipTests

# Stage 2: Runtime stage
FROM amazoncorretto:11
WORKDIR /app

# Create a non-root user for security
RUN groupadd -r appgroup && useradd -r -g appgroup appuser

# Install Payara Micro to run the WAR file
# Payara Micro is a lightweight runtime for Jakarta EE applications
RUN curl -sL https://payara.fish/downloads/payara-micro-6.2025.3-full.jar -o /app/payara-micro.jar

# Copy the built WAR file from the builder stage
COPY --from=builder /workspace/target/cargo-tracker.war /app/cargo-tracker.war

# Set environment variables for JVM and Application
ENV JAVA_OPTS="-Xmx512m -Xms256m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"
ENV TZ=UTC
ENV SPRING_PROFILES_ACTIVE=docker

# Expose the default Payara Micro port
EXPOSE 8080

# Change ownership to non-root user
RUN chown -R appuser:appgroup /app
USER appuser

# Start the application using Payara Micro
# --deploy: deploys the specified WAR file
# --port: sets the HTTP port
ENTRYPOINT ["java", "-jar", "/app/payara-micro.jar", "--deploy", "/app/cargo-tracker.war", "--port", "8080"]
