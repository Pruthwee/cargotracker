FROM maven:3.9.4-eclipse-temurin-11 AS builder

WORKDIR /workspace

COPY pom.xml .
COPY src ./src

RUN mvn -B clean package -Pcloud -DskipTests

RUN mvn -B org.apache.maven.plugins:maven-dependency-plugin:3.6.1:copy -Dartifact=fish.payara.extras:payara-micro:6.2025.3:jar -DoutputDirectory=/workspace/payara

FROM eclipse-temurin:11-jdk-alpine

LABEL maintainer="Eclipse Cargo Tracker"

ENV TZ=UTC \
    JAVA_OPTS="-Xmx512m -Xms256m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0" \
    DB_DRIVER_CLASS="org.postgresql.ds.PGPoolingDataSource" \
    DB_JDBC_URL="jdbc:postgresql://localhost:5432/postgres" \
    DB_USER="" \
    DB_PASSWORD="" \
    GRAPH_TRAVERSAL_URL="http://localhost:8080/rest/graph-traversal/shortest-path"

COPY --from=builder /workspace/target/cargo-tracker.war /app/cargo-tracker.war
COPY --from=builder /workspace/target/postgresql.jar /app/postgresql.jar
COPY --from=builder /workspace/payara/payara-micro-6.2025.3.jar /app/payara-micro.jar

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/payara-micro.jar --deploy /app/cargo-tracker.war --contextroot / --addLibs /app/postgresql.jar --httpPort 8080"]
