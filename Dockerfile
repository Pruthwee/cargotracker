# syntax=docker/dockerfile:1

# ---------- Build stage: Maven on Java 11 ----------
FROM maven:3.9.4-eclipse-temurin-11 AS builder

WORKDIR /workspace

# Dependencies first, so that they are cached while only the source changes
COPY pom.xml .
RUN mvn -B -Pcloud dependency:go-offline

COPY src ./src

# The cloud profile builds the WAR for PostgreSQL. The data source settings are not baked into
# the image: they are Payara variables, resolved from the container's environment at start-up.
RUN mvn -B clean package -Pcloud -DskipTests \
      '-DpostgreSqlJdbcUrl=${ENV=DB_JDBC_URL}' \
      '-DpostgreSqlUsername=${ENV=DB_USER}' \
      '-DpostgreSqlPassword=${ENV=DB_PASSWORD}' \
 && mvn -B -Pcloud dependency:copy-dependencies \
      -DincludeArtifactIds=payara-micro -DincludeScope=test \
      -DoutputDirectory=target/payara -Dmdep.stripVersion=true

# ---------- Runtime stage: Payara Micro on Amazon Corretto 11 ----------
FROM amazoncorretto:11

ENV HOME=/app \
    TZ=UTC \
    LANG=en_US.UTF-8 \
    LC_ALL=en_US.UTF-8 \
    JAVA_OPTS="-Xmx512m -Xms256m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Dfile.encoding=UTF-8 -Duser.timezone=UTC -Djava.security.egd=file:/dev/./urandom"

WORKDIR /app

# Payara Micro runs the application; a WAR named ROOT.war is deployed at the context root "/"
COPY --from=builder --chown=1001:1001 /workspace/target/payara/payara-micro*.jar /app/payara-micro.jar
COPY --from=builder --chown=1001:1001 /workspace/target/postgresql.jar /app/postgresql.jar
COPY --from=builder --chown=1001:1001 /workspace/target/cargo-tracker.war /app/ROOT.war

# Non-root user
USER 1001:1001

EXPOSE 8080

# exec: java, not the shell, is the first process and receives the platform's SIGTERM
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/payara-micro.jar --nocluster --port 8080 --addLibs /app/postgresql.jar --deploy /app/ROOT.war"]
