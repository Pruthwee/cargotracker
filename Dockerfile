FROM maven:3.9.4-eclipse-temurin-11 AS builder

WORKDIR /workspace

COPY pom.xml ./
RUN mvn dependency:go-offline -DskipTests
RUN mvn dependency:copy -Dartifact=fish.payara.extras:payara-micro:6.2025.3 -DoutputDirectory=/workspace/runtime -Dmdep.stripVersion=true

COPY . .

ARG POSTGRESQL_JDBC_URL="jdbc:postgresql://postgres:5432/postgres"
ARG POSTGRESQL_USERNAME="postgres"
ARG POSTGRESQL_PASSWORD="postgres"
ARG GRAPH_TRAVERSAL_URL="http://localhost:8080/rest/graph-traversal/shortest-path"

RUN mvn clean package -Pcloud -DskipTests \
    -DpostgreSqlJdbcUrl="${POSTGRESQL_JDBC_URL}" \
    -DpostgreSqlUsername="${POSTGRESQL_USERNAME}" \
    -DpostgreSqlPassword="${POSTGRESQL_PASSWORD}" \
    -Dwebapp.graphTraversalUrl="${GRAPH_TRAVERSAL_URL}"

FROM eclipse-temurin:11-jdk

LABEL org.opencontainers.image.title="cargo-tracker" \
      org.opencontainers.image.description="Jakarta EE Cargo Tracker application packaged for Azure AKS" \
      org.opencontainers.image.source="cargotracker_comp"

ENV APP_HOME=/app \
    TZ=UTC \
    LANG=C.UTF-8 \
    LC_ALL=C.UTF-8 \
    JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Dfile.encoding=UTF-8" \
    PAYARA_ARGS="--nocluster --nohazelcast --port 8080" \
    POSTBOOT_COMMANDS=/app/config/post-boot-commands.asadmin

WORKDIR ${APP_HOME}

RUN groupadd -r appgroup && useradd -r -g appgroup -d ${APP_HOME} -s /usr/sbin/nologin appuser

COPY --from=builder /workspace/runtime/payara-micro.jar ${APP_HOME}/payara-micro.jar
COPY --from=builder /workspace/target/postgresql.jar ${APP_HOME}/lib/postgresql.jar
COPY --from=builder /workspace/target/cargo-tracker.war ${APP_HOME}/deployments/cargo-tracker.war
COPY post-boot-commands.asadmin ${APP_HOME}/config/post-boot-commands.asadmin

RUN chown -R appuser:appgroup ${APP_HOME}

USER appuser

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar ${APP_HOME}/payara-micro.jar $PAYARA_ARGS --addlibs ${APP_HOME}/lib/postgresql.jar --postbootcommandfile ${POSTBOOT_COMMANDS} --deploy ${APP_HOME}/deployments/cargo-tracker.war"]
