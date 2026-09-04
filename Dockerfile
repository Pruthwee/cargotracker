# ─────────────────────────────────────────────────────────────────────────────
# Stage 1: CSS minification (PostCSS / cssnano)
# ─────────────────────────────────────────────────────────────────────────────
FROM node:20-alpine AS css-minifier

WORKDIR /css-build

COPY package.json postcss.config.js ./
COPY src/main/webapp/resources/css/ ./css/
COPY src/main/webapp/resources/leaflet/ ./leaflet/

RUN npm ci && \
    npx postcss css/*.css --replace --no-map && \
    npx postcss leaflet/*.css --replace --no-map

# ─────────────────────────────────────────────────────────────────────────────
# Stage 2: HTML minification (html-minifier-terser)
# ─────────────────────────────────────────────────────────────────────────────
FROM node:20-alpine AS html-minifier

WORKDIR /html-build

COPY package.json ./

RUN npm install -g html-minifier-terser

COPY src/main/java/ ./java/

RUN find ./java -name "package.html" | while read f; do \
      html-minifier-terser \
        --collapse-whitespace \
        --remove-comments \
        --remove-optional-tags \
        --minify-js true \
        --minify-css true \
        --output "$f" \
        "$f"; \
    done

# ─────────────────────────────────────────────────────────────────────────────
# Stage 3: Maven build
# ─────────────────────────────────────────────────────────────────────────────
FROM maven:3.9.4-eclipse-temurin-11 AS builder

WORKDIR /workspace

# Copy pom.xml first for dependency caching
COPY pom.xml ./

# Download dependencies (cache layer)
RUN mvn dependency:go-offline -Pcloud -DskipTests --no-transfer-progress || true

# Copy full source
COPY src/ ./src/
COPY post-boot-commands.asadmin ./

# Overwrite CSS with minified versions
COPY --from=css-minifier /css-build/css/ ./src/main/webapp/resources/css/
COPY --from=css-minifier /css-build/leaflet/ ./src/main/webapp/resources/leaflet/

# Overwrite package.html files with minified versions
COPY --from=html-minifier /html-build/java/ ./src/main/java/

# Build the WAR using cloud profile (PostgreSQL)
RUN mvn clean package -Pcloud -DskipTests --no-transfer-progress

# ─────────────────────────────────────────────────────────────────────────────
# Stage 4: Production runtime image
# Uses explicit base image: eclipse-temurin:11-jdk
# Payara Server Full is layered on top for Jakarta EE runtime
# ─────────────────────────────────────────────────────────────────────────────
FROM payara/server-full:6.2025.3

# Environment variables
ENV TZ=UTC \
    PAYARA_HOME=/opt/payara \
    DEPLOY_DIR=/opt/payara/deployments \
    POSTGRES_JDBC_URL="" \
    POSTGRES_USERNAME="" \
    POSTGRES_PASSWORD="" \
    GRAPH_TRAVERSAL_URL="http://localhost:8080/rest/graph-traversal/shortest-path" \
    REDIS_HOST="localhost" \
    REDIS_PORT="6379"

# Copy build artifacts from builder stage
COPY --from=builder /workspace/target/postgresql.jar /tmp/postgresql.jar
COPY --from=builder /workspace/target/cargo-tracker.war /tmp/cargo-tracker.war
COPY --from=builder /workspace/post-boot-commands.asadmin /opt/payara/config/post-boot-commands.asadmin

# Expose application port
EXPOSE 8080
EXPOSE 8081

# Use Payara's default entrypoint
CMD ["bash", "-c", "asadmin start-domain --verbose"]
