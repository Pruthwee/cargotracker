# Stage 1: Build stage
FROM maven:3.9.4-eclipse-temurin-11 AS builder
WORKDIR /workspace

# Copy pom.xml first for dependency caching
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code
COPY src ./src

# Build the application
# Using -Pcloud profile as it's intended for production/cloud deployment
RUN mvn clean package -Pcloud -DskipTests

# Stage 2: Runtime stage
FROM amazoncorretto:11
WORKDIR /app

# Create a non-root user for security
RUN groupadd -r appuser && useradd -r -g appuser appuser

# Copy the WAR file from the builder stage
# The pom.xml defines <finalName>cargo-tracker</finalName>
COPY --from=builder /workspace/target/cargo-tracker.war app.war

# This application is a Jakarta EE WAR. It requires an application server.
# Since the project uses Payara Micro in its profiles, we will use Payara Micro for the runtime.
# We install Payara Micro in the runtime image.
RUN curl -L https://github.com/payara/payara-micro/releases/latest/download/payara-micro.jar -o /app/payara-micro.jar

# Set environment variables
ENV TZ=UTC
ENV JAVA_OPTS="-Xmx512m -Xms256m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"
ENV SPRING_PROFILES_ACTIVE=docker

# Expose the default Payara Micro port
EXPOSE 8080

# Change ownership to non-root user
RUN chown -R appuser:appuser /app
USER appuser

# Start Payara Micro with the application WAR
ENTRYPOINT ["java", "-Xmx512m", "-Xms256m", "-jar", "/app/payara-micro.jar", "--deploy", "app.war"]
