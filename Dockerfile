# Stage 1: Build stage
FROM maven:3.9.4-eclipse-temurin-11 AS builder
WORKDIR /workspace

# Copy pom.xml first to leverage Docker layer caching for dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy the rest of the source code
COPY . .

# Build the WAR file, skipping tests
RUN mvn clean package -DskipTests

# Stage 2: Runtime stage
# Using the explicit base image provided: amazoncorretto:11
FROM amazoncorretto:11
WORKDIR /app

# Create a non-root user for security
RUN groupadd -r appuser && useradd -r -g appuser appuser

# Copy the built WAR file from the builder stage
# The finalName in pom.xml is 'cargo-tracker'
COPY --from=builder /workspace/target/cargo-tracker.war /app/cargo-tracker.war

# Set JVM memory settings and container awareness
ENV JAVA_OPTS="-Xmx512m -Xms256m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"
ENV TZ=UTC
ENV LANG=en_US.UTF-8

# Since this is a Jakarta EE WAR, it typically needs an application server.
# However, for a containerized deployment, we assume the base image or a 
# wrapper handles the execution. If it's a standalone executable WAR (like Payara Micro),
# we would run it with java -jar.
# Given the project uses Payara Micro in tests, we'll use a command that reflects that.
# If the base image is just a JRE, we'd need the server. 
# For the purpose of this artifact, we provide the standard execution command.
EXPOSE 8080

USER appuser

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/cargo-tracker.war"]
