# ============================================================
# Stage 1: Builder
# ============================================================
FROM maven:3.9.4-eclipse-temurin-11 AS builder

WORKDIR /workspace

# Copy pom.xml first for dependency caching
COPY pom.xml .

# Download dependencies (cached layer)
RUN mvn dependency:go-offline -Ppayara -DskipTests --batch-mode

# Copy the full source code
COPY src ./src

# Build the WAR (using cloud profile for PostgreSQL support)
RUN mvn clean package -Pcloud -DskipTests --batch-mode \
    -DpostgreSqlJdbcUrl="jdbc:postgresql://localhost:5432/cargotracker" \
    -DpostgreSqlUsername="postgres" \
    -DpostgreSqlPassword="postgres"

# ============================================================
# Stage 2: Runtime
# ============================================================
FROM amazoncorretto:11

LABEL maintainer="Eclipse Cargo Tracker" \
      application="cargo-tracker" \
      version="3.1-SNAPSHOT"

# Environment variables
ENV PAYARA_VERSION=6.2025.3 \
    PAYARA_HOME=/opt/payara \
    DEPLOY_DIR=/opt/payara/glassfish/domains/domain1/autodeploy \
    TZ=UTC \
    JAVA_OPTS="-Xmx512m -Xms256m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UnlockExperimentalVMOptions"

# Install required tools
RUN yum install -y wget unzip \
    && yum clean all \
    && rm -rf /var/cache/yum

# Download and install Payara
RUN wget -q "https://nexus.payara.fish/repository/payara-community/fish/payara/distributions/payara/${PAYARA_VERSION}/payara-${PAYARA_VERSION}.zip" \
        -O /tmp/payara.zip \
    && unzip -q /tmp/payara.zip -d /opt \
    && mv /opt/payara6 ${PAYARA_HOME} \
    && rm /tmp/payara.zip

# Create non-root user
RUN groupadd -r payara && useradd -r -g payara -d ${PAYARA_HOME} payara \
    && chown -R payara:payara ${PAYARA_HOME}

# Copy WAR and PostgreSQL driver from builder
COPY --from=builder /workspace/target/cargo-tracker.war ${DEPLOY_DIR}/cargo-tracker.war
COPY --from=builder /workspace/target/postgresql.jar ${PAYARA_HOME}/glassfish/domains/domain1/lib/postgresql.jar

# Copy post-boot commands
COPY post-boot-commands.asadmin ${PAYARA_HOME}/config/post-boot-commands.asadmin

# Set ownership
RUN chown -R payara:payara ${PAYARA_HOME}

USER payara

WORKDIR ${PAYARA_HOME}

# Expose application port
EXPOSE 8080
EXPOSE 8081
EXPOSE 4848

# Start Payara with the WAR deployed
CMD ["bin/startInForeground.sh", "--postbootcommandfile", "/opt/payara/config/post-boot-commands.asadmin", "--deploy", "/opt/payara/glassfish/domains/domain1/autodeploy/cargo-tracker.war", "--contextroot", "/cargo-tracker", "--name", "cargo-tracker"]
