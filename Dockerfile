# Build stage
FROM maven:3.9.4-eclipse-temurin-11 AS builder
WORKDIR /workspace
COPY pom.xml .
RUN mvn dependency:go-offline -Ppayara && \
    mvn clean package -DskipTests -Ppayara && \
    mvn dependency:copy -Dartifact=fish.payara.extras:payara-micro:6.2025.3 -DoutputDirectory=/workspace/target/

# Runtime stage
FROM amazoncorretto:11
WORKDIR /app
COPY --from=builder /workspace/target/cargo-tracker.war app.war
COPY --from=builder /workspace/target/payara-micro-*.jar payara-micro.jar
EXPOSE 8080
USER 1000
ENV JAVA_OPTS="-Xmx512m -Xms256m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"
ENV TZ=UTC
ENV LANG='en_US.UTF-8' LC_ALL='en_US.UTF-8'
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar payara-micro.jar --deploy app.war --httpPort 8080 --contextroot cargo-tracker"]
