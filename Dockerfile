FROM maven:3.9.4-eclipse-temurin-11 AS builder

WORKDIR /workspace

COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline -Pcloud \
    -DpostgreSqlJdbcUrl=jdbc:postgresql://postgres.example.com:5432/postgres \
    -DpostgreSqlUsername=cargotracker \
    -DpostgreSqlPassword=cargotracker

COPY src ./src
COPY post-boot-commands.asadmin ./post-boot-commands.asadmin

ARG POSTGRESQL_JDBC_URL=jdbc:postgresql://postgres.example.com:5432/postgres
ARG POSTGRESQL_USERNAME=cargotracker
ARG POSTGRESQL_PASSWORD=cargotracker

RUN mvn -B -ntp clean package -Pcloud -DskipTests \
    -DpostgreSqlJdbcUrl=${POSTGRESQL_JDBC_URL} \
    -DpostgreSqlUsername=${POSTGRESQL_USERNAME} \
    -DpostgreSqlPassword=${POSTGRESQL_PASSWORD}
RUN mvn -B -ntp dependency:copy \
    -Dartifact=fish.payara.extras:payara-micro:6.2025.3 \
    -DoutputDirectory=/workspace/target \
    -DdestFileName=payara-micro.jar

FROM eclipse-temurin:11-jre

LABEL org.opencontainers.image.title="cargo-tracker" \
      org.opencontainers.image.description="Jakarta EE Cargo Tracker application running on Payara Micro" \
      org.opencontainers.image.source="https://github.com/eclipse-ee4j/cargotracker"

WORKDIR /app

ENV TZ=UTC \
    LANG=C.UTF-8 \
    LC_ALL=C.UTF-8 \
    JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Dfile.encoding=UTF-8" \
    PAYARA_ARGS="--noCluster"

COPY --from=builder /workspace/target/payara-micro.jar /app/payara-micro.jar
COPY --from=builder /workspace/target/cargo-tracker.war /opt/payara/deployments/cargo-tracker.war
COPY --from=builder /workspace/target/postgresql.jar /opt/payara/libs/postgresql.jar
COPY post-boot-commands.asadmin /opt/payara/config/post-boot-commands.asadmin

RUN useradd --system --uid 10001 --gid 0 --home-dir /app --shell /usr/sbin/nologin appuser \
    && mkdir -p /app /opt/payara/deployments /opt/payara/libs /opt/payara/config /app/data \
    && chown -R appuser:0 /app /opt/payara \
    && chmod -R g=u /app /opt/payara

USER 10001

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/payara-micro.jar --addLibs /opt/payara/libs/postgresql.jar --postbootcommandfile /opt/payara/config/post-boot-commands.asadmin $PAYARA_ARGS"]
