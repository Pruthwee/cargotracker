# ============================================================
# Stage 1: Build
# ============================================================
FROM maven:3.9.4-eclipse-temurin-11 AS builder

WORKDIR /workspace

# Copy dependency descriptors first for layer caching
COPY pom.xml .

# Download dependencies (offline cache layer)
RUN mvn dependency:go-offline -B -q

# Copy full source tree
COPY src ./src

# Build the WAR (cloud profile uses PostgreSQL; default payara profile uses H2)
RUN mvn clean package -DskipTests -B -q

# ============================================================
# Stage 2: Runtime
# ============================================================
FROM amazoncorretto:11

LABEL maintainer="Eclipse Cargo Tracker" \
      application="cargo-tracker" \
      version="3.1-SNAPSHOT"

# Environment
ENV TZ=UTC \
    LANG=en_US.UTF-8 \
    JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UnlockExperimentalVMOptions" \
    PAYARA_VERSION=6.2025.3 \
    PAYARA_HOME=/opt/payara \
    DEPLOY_DIR=/opt/payara/glassfish/domains/domain1/autodeploy \
    REDIS_HOST=localhost \
    REDIS_PORT=6379 \
    REDIS_PASSWORD="" \
    DB_JDBC_URL="jdbc:h2:file:./cargo-tracker-data/cargo-tracker-database" \
    DB_USER="" \
    DB_PASSWORD="" \
    GRAPH_TRAVERSAL_URL="http://localhost:8080/cargo-tracker/rest/graph-traversal/shortest-path"

# Install Payara Micro as the Jakarta EE runtime
RUN yum install -y wget tar gzip \
    && mkdir -p ${PAYARA_HOME} \
    && wget -q "https://repo1.maven.org/maven2/fish/payara/extras/payara-micro/${PAYARA_VERSION}/payara-micro-${PAYARA_VERSION}.jar" \
         -O ${PAYARA_HOME}/payara-micro.jar \
    && yum clean all \
    && rm -rf /var/cache/yum

# Create non-root user
RUN groupadd -r cargotracker && useradd -r -g cargotracker -d /home/cargotracker -s /sbin/nologin cargotracker \
    && mkdir -p /home/cargotracker/cargo-tracker-data \
    && chown -R cargotracker:cargotracker /home/cargotracker ${PAYARA_HOME}

WORKDIR /home/cargotracker

# Copy the built WAR from builder stage
COPY --from=builder /workspace/target/cargo-tracker.war ./cargo-tracker.war
RUN chown cargotracker:cargotracker ./cargo-tracker.war

USER cargotracker

EXPOSE 8080 8181

ENTRYPOINT ["sh", "-c", \
  "java ${JAVA_OPTS} -jar ${PAYARA_HOME}/payara-micro.jar \
    --deploy /home/cargotracker/cargo-tracker.war \
    --port 8080 \
    --sslPort 8181 \
    --noCluster"]
