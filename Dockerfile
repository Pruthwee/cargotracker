# Build stage
FROM maven:3.9.4-eclipse-temurin-17 AS builder

WORKDIR /workspace

# Copy pom.xml first and download dependencies
COPY pom.xml .
RUN mvn -B dependency:go-offline -Pcloud

# Copy source code and build
COPY src ./src
RUN mvn -B clean package -Pcloud -DskipTests

# Download Payara Micro for the runtime stage
RUN mvn -B dependency:copy -Dartifact=fish.payara.extras:payara-micro:6.2025.3 -DoutputDirectory=/workspace

# Runtime stage
FROM amazoncorretto:11

# Create a non-root user
RUN groupadd -r appuser && useradd -r -g appuser appuser

WORKDIR /home/appuser

# Copy the WAR file, PostgreSQL driver, and Payara Micro
COPY --from=builder /workspace/target/cargo-tracker.war cargo-tracker.war
COPY --from=builder /workspace/target/postgresql.jar postgresql.jar
COPY --from=builder /workspace/payara-micro-6.2025.3.jar payara-micro.jar

# Set ownership
RUN chown -R appuser:appuser /home/appuser

USER appuser

# Environment variables
ENV TZ=UTC
ENV JAVA_OPTS="-Xmx512m -Xms256m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"
ENV DB_DRIVER_CLASS="org.postgresql.ds.PGPoolingDataSource"
ENV DB_JDBC_URL="jdbc:postgresql://localhost:5432/postgres"
ENV DB_USER=""
ENV DB_PASSWORD=""
ENV GRAPH_TRAVERSAL_URL="http://localhost:8080/rest/graph-traversal/shortest-path"

# Expose the application port
EXPOSE 8080

# Run Payara Micro with the WAR and PostgreSQL driver
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar payara-micro.jar --port 8080 --deploy cargo-tracker.war --addlibs postgresql.jar"]
