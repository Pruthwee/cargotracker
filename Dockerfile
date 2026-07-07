# ============================================================
# Stage 1: Builder
# ============================================================
FROM maven:3.9.4-eclipse-temurin-11 AS builder

WORKDIR /workspace

# Copy Maven build descriptor first for dependency caching
COPY pom.xml .

# Download all dependencies (leverages Docker layer cache)
RUN mvn dependency:go-offline -B -q

# Copy the full project source
COPY src ./src
COPY post-boot-commands.asadmin .

# Build the WAR (skip tests for Docker build)
RUN mvn clean package -DskipTests -B -q

# ============================================================
# Stage 2: Runtime
# ============================================================
FROM amazoncorretto:11

LABEL maintainer="Eclipse Cargo Tracker"
LABEL description="Eclipse Cargo Tracker - Jakarta EE 10 application on Payara Micro"
LABEL version="3.1-SNAPSHOT"

# Environment variables
ENV PAYARA_VERSION=6.2025.3 \
    PAYARA_HOME=/opt/payara \
    DEPLOY_DIR=/opt/payara/deployments \
    TZ=UTC \
    JAVA_OPTS="-Xmx512m -Xms256m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UnlockExperimentalVMOptions"

# Install required tools
RUN yum install -y wget tar gzip \
    && yum clean all \
    && rm -rf /var/cache/yum

# Create Payara directories
RUN mkdir -p ${PAYARA_HOME} ${DEPLOY_DIR} /opt/payara/config /opt/payara/data

# Download Payara Micro
RUN wget -q "https://repo1.maven.org/maven2/fish/payara/extras/payara-micro/${PAYARA_VERSION}/payara-micro-${PAYARA_VERSION}.jar" \
    -O ${PAYARA_HOME}/payara-micro.jar

# Create non-root user for security
RUN groupadd -r payara && useradd -r -g payara -d ${PAYARA_HOME} -s /sbin/nologin payara \
    && chown -R payara:payara ${PAYARA_HOME}

# Copy WAR artifact from builder stage
COPY --from=builder /workspace/target/cargo-tracker.war ${DEPLOY_DIR}/cargo-tracker.war

# Copy post-boot commands
COPY --from=builder /workspace/post-boot-commands.asadmin /opt/payara/config/post-boot-commands.asadmin

# Set ownership
RUN chown -R payara:payara ${PAYARA_HOME}

# Switch to non-root user
USER payara

# Expose application port
EXPOSE 8080

# Expose HTTPS port
EXPOSE 8181

# Start Payara Micro with the deployed WAR
ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -jar ${PAYARA_HOME}/payara-micro.jar \
    --deploy ${DEPLOY_DIR}/cargo-tracker.war \
    --contextroot / \
    --port 8080 \
    --sslport 8181 \
    --postbootcommandfile /opt/payara/config/post-boot-commands.asadmin \
    --nocluster"]
