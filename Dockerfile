FROM maven:3.9.4-eclipse-temurin-11 AS builder

WORKDIR /workspace

COPY pom.xml .

RUN mvn dependency:go-offline -Pcloud

COPY src ./src

RUN mvn clean package -Pcloud -DskipTests

# Download Payara Micro for running the WAR
RUN mvn dependency:copy -Dartifact=fish.payara.extras:payara-micro:6.2025.3:jar -DoutputDirectory=/workspace

FROM amazoncorretto:11

RUN groupadd -r cargo-tracker && useradd -r -g cargo-tracker cargo-tracker

WORKDIR /app

COPY --from=builder /workspace/target/cargo-tracker.war /app/cargo-tracker.war
COPY --from=builder /workspace/target/postgresql.jar /app/postgresql.jar
COPY --from=builder /workspace/payara-micro-6.2025.3.jar /app/payara-micro.jar

ENV JAVA_OPTS="-Xmx512m -Xms256m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0" \
    TZ=UTC \
    LANG=en_US.UTF-8 \
    LC_ALL=en_US.UTF-8 \
    DB_DRIVER_CLASS="org.postgresql.ds.PGPoolingDataSource" \
    DB_JDBC_URL="jdbc:postgresql://postgres:5432/postgres" \
    DB_USER="cargotracker" \
    DB_PASSWORD="cargotracker" \
    GRAPH_TRAVERSAL_URL="http://localhost:8080/rest/graph-traversal/shortest-path"

EXPOSE 8080

USER cargo-tracker

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/payara-micro.jar --deploy /app/cargo-tracker.war --contextroot / --addlibs /app/postgresql.jar --httpPort 8080"]
