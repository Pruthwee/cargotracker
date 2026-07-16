# Stage 1: Build stage
FROM maven:3.9.4-eclipse-temurin-11 AS builder
WORKDIR /workspace

# Copy pom.xml first for dependency caching
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code
COPY src ./src

# Build the application
# Using the 'payara' profile as it is active by default and provides H2 for basic runtime
RUN mvn clean package -DskipTests

# Stage 2: Runtime stage
# Using the explicit base image provided in the request
FROM eclipse-temurin:11-jdk
WORKDIR /app

# Create a non-root user for security
RUN groupadd -r appgroup && useradd -r -g appgroup appuser

# Copy the WAR file from the builder stage
COPY --from=builder /workspace/target/cargo-tracker.war app.war

# Set JVM memory settings and container optimizations
ENV JAVA_OPTS="-Xmx512m -Xms256m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"
ENV TZ=UTC

# Since this is a Jakarta EE WAR, it needs a runtime like Payara Micro or Open Liberty.
# For a production-ready EKS deployment, we assume the runtime is provided or 
# we use a lightweight runtime. Here we use Payara Micro for execution.
RUN curl -L https://github.com/payara/payara-micro/releases/latest/download/payara-micro.jar -o /app/payara-micro.jar

USER appuser

# Expose the default application port
EXPOSE 8080

# Start the application using Payara Micro
ENTRYPOINT ["java", "-jar", "/app/payara-micro.jar", "--deploy", "app.war"]
CMD ["--port", "8080"]
