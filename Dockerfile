# syntax=docker/dockerfile:1.5
FROM maven:3.9.4-eclipse-temurin-11 AS builder

WORKDIR /workspace

COPY pom.xml .
RUN mvn -B dependency:go-offline -Pcloud -DskipTests || true

COPY src ./src

ARG POSTGRESQL_JDBC_URL=jdbc:postgresql://postgresql.example.com:5432/postgres
ARG POSTGRESQL_USERNAME=postgres
ARG POSTGRESQL_PASSWORD=postgres

RUN mvn -B clean package -Pcloud -DskipTests \
    -DpostgreSqlJdbcUrl="${POSTGRESQL_JDBC_URL}" \
    -DpostgreSqlUsername="${POSTGRESQL_USERNAME}" \
    -DpostgreSqlPassword="${POSTGRESQL_PASSWORD}"

RUN mvn -B dependency:copy \
    -Dartifact=fish.payara.extras:payara-micro:6.2025.3 \
    -DoutputDirectory=target/dependency \
    -Dmdep.stripVersion=true

FROM eclipse-temurin:11-jre

WORKDIR /app

ENV PORT=8080 \
    TZ=UTC \
    LANG=C.UTF-8 \
    LC_ALL=C.UTF-8 \
    JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Dfile.encoding=UTF-8 -Djava.awt.headless=true"

RUN groupadd --system cargotracker && \
    useradd --system --gid cargotracker --home-dir /app --shell /usr/sbin/nologin cargotracker

COPY --from=builder /workspace/target/cargo-tracker.war /app/cargo-tracker.war
COPY --from=builder /workspace/target/dependency/payara-micro.jar /app/payara-micro.jar

RUN chown -R cargotracker:cargotracker /app

USER cargotracker

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/payara-micro.jar --deploy /app/cargo-tracker.war --contextRoot / --port ${PORT}"]
