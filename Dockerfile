# ── Stage 1: CSS minification ────────────────────────────────────────────────
FROM node:20-alpine AS css-minifier

WORKDIR /css-build

COPY package.json ./

RUN npm install --save-dev postcss postcss-cli cssnano

COPY src/main/webapp/resources/css/ ./css/
COPY src/main/webapp/resources/leaflet/leaflet.css ./leaflet/leaflet.css

COPY postcss.config.js ./

RUN mkdir -p css leaflet && \
    npx postcss css/app.css         --use cssnano --no-map -o css/app.min.css         && \
    npx postcss css/dd.css          --use cssnano --no-map -o css/dd.min.css          && \
    npx postcss css/title.css       --use cssnano --no-map -o css/title.min.css       && \
    npx postcss leaflet/leaflet.css --use cssnano --no-map -o leaflet/leaflet.min.css && \
    mv css/app.min.css         css/app.css         && \
    mv css/dd.min.css          css/dd.css          && \
    mv css/title.min.css       css/title.css       && \
    mv leaflet/leaflet.min.css leaflet/leaflet.css

# ── Stage 2: HTML minification ────────────────────────────────────────────────
FROM node:20-alpine AS html-minifier

WORKDIR /html-build

COPY package.json ./

RUN npm install --save-dev html-minifier-terser

COPY src/main/java/org/eclipse/cargotracker/application/internal/package.html \
     ./html/application/internal/package.html
COPY src/main/java/org/eclipse/cargotracker/application/package.html \
     ./html/application/package.html
COPY src/main/java/org/eclipse/cargotracker/application/util/package.html \
     ./html/application/util/package.html
COPY src/main/java/org/eclipse/cargotracker/domain/model/cargo/package.html \
     ./html/domain/model/cargo/package.html
COPY src/main/java/org/eclipse/cargotracker/domain/model/handling/package.html \
     ./html/domain/model/handling/package.html
COPY src/main/java/org/eclipse/cargotracker/domain/model/location/package.html \
     ./html/domain/model/location/package.html
COPY src/main/java/org/eclipse/cargotracker/domain/model/package.html \
     ./html/domain/model/package.html
COPY src/main/java/org/eclipse/cargotracker/domain/model/voyage/package.html \
     ./html/domain/model/voyage/package.html
COPY src/main/java/org/eclipse/cargotracker/domain/package.html \
     ./html/domain/package.html
COPY src/main/java/org/eclipse/cargotracker/domain/service/package.html \
     ./html/domain/service/package.html
COPY src/main/java/org/eclipse/cargotracker/domain/shared/package.html \
     ./html/domain/shared/package.html
COPY src/main/java/org/eclipse/cargotracker/infrastructure/events/cdi/package.html \
     ./html/infrastructure/events/cdi/package.html
COPY src/main/java/org/eclipse/cargotracker/infrastructure/logging/package.html \
     ./html/infrastructure/logging/package.html
COPY src/main/java/org/eclipse/cargotracker/infrastructure/messaging/jms/package.html \
     ./html/infrastructure/messaging/jms/package.html
COPY src/main/java/org/eclipse/cargotracker/infrastructure/persistence/jpa/package.html \
     ./html/infrastructure/persistence/jpa/package.html
COPY src/main/java/org/eclipse/cargotracker/infrastructure/routing/package.html \
     ./html/infrastructure/routing/package.html
COPY src/main/java/org/eclipse/cargotracker/interfaces/booking/facade/dto/package.html \
     ./html/interfaces/booking/facade/dto/package.html
COPY src/main/java/org/eclipse/cargotracker/interfaces/booking/facade/internal/assembler/package.html \
     ./html/interfaces/booking/facade/internal/assembler/package.html
COPY src/main/java/org/eclipse/cargotracker/interfaces/booking/facade/internal/package.html \
     ./html/interfaces/booking/facade/internal/package.html
COPY src/main/java/org/eclipse/cargotracker/interfaces/booking/facade/package.html \
     ./html/interfaces/booking/facade/package.html
COPY src/main/java/org/eclipse/cargotracker/interfaces/booking/sse/package.html \
     ./html/interfaces/booking/sse/package.html
COPY src/main/java/org/eclipse/cargotracker/interfaces/booking/web/package.html \
     ./html/interfaces/booking/web/package.html
COPY src/main/java/org/eclipse/cargotracker/interfaces/handling/file/package.html \
     ./html/interfaces/handling/file/package.html
COPY src/main/java/org/eclipse/cargotracker/interfaces/handling/mobile/package.html \
     ./html/interfaces/handling/mobile/package.html
COPY src/main/java/org/eclipse/cargotracker/interfaces/handling/package.html \
     ./html/interfaces/handling/package.html
COPY src/main/java/org/eclipse/cargotracker/interfaces/handling/rest/package.html \
     ./html/interfaces/handling/rest/package.html
COPY src/main/java/org/eclipse/cargotracker/interfaces/package.html \
     ./html/interfaces/package.html
COPY src/main/java/org/eclipse/cargotracker/interfaces/tracking/web/package.html \
     ./html/interfaces/tracking/web/package.html
COPY src/main/java/org/eclipse/pathfinder/api/package.html \
     ./html/pathfinder/api/package.html
COPY src/main/java/org/eclipse/pathfinder/internal/package.html \
     ./html/pathfinder/internal/package.html
COPY src/main/java/org/eclipse/pathfinder/package.html \
     ./html/pathfinder/package.html

RUN find ./html -name "package.html" | while read f; do \
      npx html-minifier-terser \
        --collapse-whitespace \
        --remove-comments \
        --minify-js true \
        --minify-css true \
        "$f" -o "$f"; \
    done

# ── Stage 3: Maven build ──────────────────────────────────────────────────────
FROM maven:3.9.4-eclipse-temurin-11 AS maven-builder

WORKDIR /build

# Copy pom.xml first for dependency caching
COPY pom.xml ./

# Download dependencies (cache layer)
RUN mvn dependency:go-offline -Pcloud -q || true

# Copy full project source
COPY . .

# Replace CSS source files with minified versions from Stage 1
COPY --from=css-minifier /css-build/css/     src/main/webapp/resources/css/
COPY --from=css-minifier /css-build/leaflet/ src/main/webapp/resources/leaflet/

# Replace package.html files with minified versions from Stage 2
COPY --from=html-minifier /html-build/html/application/internal/package.html \
     src/main/java/org/eclipse/cargotracker/application/internal/package.html
COPY --from=html-minifier /html-build/html/application/package.html \
     src/main/java/org/eclipse/cargotracker/application/package.html
COPY --from=html-minifier /html-build/html/application/util/package.html \
     src/main/java/org/eclipse/cargotracker/application/util/package.html
COPY --from=html-minifier /html-build/html/domain/model/cargo/package.html \
     src/main/java/org/eclipse/cargotracker/domain/model/cargo/package.html
COPY --from=html-minifier /html-build/html/domain/model/handling/package.html \
     src/main/java/org/eclipse/cargotracker/domain/model/handling/package.html
COPY --from=html-minifier /html-build/html/domain/model/location/package.html \
     src/main/java/org/eclipse/cargotracker/domain/model/location/package.html
COPY --from=html-minifier /html-build/html/domain/model/package.html \
     src/main/java/org/eclipse/cargotracker/domain/model/package.html
COPY --from=html-minifier /html-build/html/domain/model/voyage/package.html \
     src/main/java/org/eclipse/cargotracker/domain/model/voyage/package.html
COPY --from=html-minifier /html-build/html/domain/package.html \
     src/main/java/org/eclipse/cargotracker/domain/package.html
COPY --from=html-minifier /html-build/html/domain/service/package.html \
     src/main/java/org/eclipse/cargotracker/domain/service/package.html
COPY --from=html-minifier /html-build/html/domain/shared/package.html \
     src/main/java/org/eclipse/cargotracker/domain/shared/package.html
COPY --from=html-minifier /html-build/html/infrastructure/events/cdi/package.html \
     src/main/java/org/eclipse/cargotracker/infrastructure/events/cdi/package.html
COPY --from=html-minifier /html-build/html/infrastructure/logging/package.html \
     src/main/java/org/eclipse/cargotracker/infrastructure/logging/package.html
COPY --from=html-minifier /html-build/html/infrastructure/messaging/jms/package.html \
     src/main/java/org/eclipse/cargotracker/infrastructure/messaging/jms/package.html
COPY --from=html-minifier /html-build/html/infrastructure/persistence/jpa/package.html \
     src/main/java/org/eclipse/cargotracker/infrastructure/persistence/jpa/package.html
COPY --from=html-minifier /html-build/html/infrastructure/routing/package.html \
     src/main/java/org/eclipse/cargotracker/infrastructure/routing/package.html
COPY --from=html-minifier /html-build/html/interfaces/booking/facade/dto/package.html \
     src/main/java/org/eclipse/cargotracker/interfaces/booking/facade/dto/package.html
COPY --from=html-minifier /html-build/html/interfaces/booking/facade/internal/assembler/package.html \
     src/main/java/org/eclipse/cargotracker/interfaces/booking/facade/internal/assembler/package.html
COPY --from=html-minifier /html-build/html/interfaces/booking/facade/internal/package.html \
     src/main/java/org/eclipse/cargotracker/interfaces/booking/facade/internal/package.html
COPY --from=html-minifier /html-build/html/interfaces/booking/facade/package.html \
     src/main/java/org/eclipse/cargotracker/interfaces/booking/facade/package.html
COPY --from=html-minifier /html-build/html/interfaces/booking/sse/package.html \
     src/main/java/org/eclipse/cargotracker/interfaces/booking/sse/package.html
COPY --from=html-minifier /html-build/html/interfaces/booking/web/package.html \
     src/main/java/org/eclipse/cargotracker/interfaces/booking/web/package.html
COPY --from=html-minifier /html-build/html/interfaces/handling/file/package.html \
     src/main/java/org/eclipse/cargotracker/interfaces/handling/file/package.html
COPY --from=html-minifier /html-build/html/interfaces/handling/mobile/package.html \
     src/main/java/org/eclipse/cargotracker/interfaces/handling/mobile/package.html
COPY --from=html-minifier /html-build/html/interfaces/handling/package.html \
     src/main/java/org/eclipse/cargotracker/interfaces/handling/package.html
COPY --from=html-minifier /html-build/html/interfaces/handling/rest/package.html \
     src/main/java/org/eclipse/cargotracker/interfaces/handling/rest/package.html
COPY --from=html-minifier /html-build/html/interfaces/package.html \
     src/main/java/org/eclipse/cargotracker/interfaces/package.html
COPY --from=html-minifier /html-build/html/interfaces/tracking/web/package.html \
     src/main/java/org/eclipse/cargotracker/interfaces/tracking/web/package.html
COPY --from=html-minifier /html-build/html/pathfinder/api/package.html \
     src/main/java/org/eclipse/pathfinder/api/package.html
COPY --from=html-minifier /html-build/html/pathfinder/internal/package.html \
     src/main/java/org/eclipse/pathfinder/internal/package.html
COPY --from=html-minifier /html-build/html/pathfinder/package.html \
     src/main/java/org/eclipse/pathfinder/package.html

# Build the WAR using the cloud profile (PostgreSQL) and skip tests
RUN mvn clean package -Pcloud -DskipTests \
    -DpostgreSqlJdbcUrl="${DB_JDBC_URL:-jdbc:postgresql://localhost:5432/postgres}" \
    -DpostgreSqlUsername="${DB_USER:-postgres}" \
    -DpostgreSqlPassword="${DB_PASSWORD:-postgres}"

# ── Stage 4: Production image ─────────────────────────────────────────────────
FROM amazoncorretto:11

# Install Payara Micro for running the Jakarta EE WAR
ENV PAYARA_VERSION=6.2025.3
ENV PAYARA_HOME=/opt/payara

RUN mkdir -p ${PAYARA_HOME} && \
    curl -fsSL "https://repo1.maven.org/maven2/fish/payara/extras/payara-micro/${PAYARA_VERSION}/payara-micro-${PAYARA_VERSION}.jar" \
         -o ${PAYARA_HOME}/payara-micro.jar

# Create non-root user for security
RUN groupadd -r payara && useradd -r -g payara -d ${PAYARA_HOME} -s /sbin/nologin payara

WORKDIR ${PAYARA_HOME}

# Copy the WAR and PostgreSQL JDBC driver from the builder stage
COPY --from=maven-builder /build/target/cargo-tracker.war ${PAYARA_HOME}/cargo-tracker.war
COPY --from=maven-builder /build/target/postgresql.jar    ${PAYARA_HOME}/postgresql.jar

RUN chown -R payara:payara ${PAYARA_HOME}

USER payara

# Environment variables
ENV TZ=UTC
ENV JAVA_OPTS="-Xmx512m -Xms256m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UnlockExperimentalVMOptions"
ENV DB_JDBC_URL="jdbc:postgresql://localhost:5432/postgres"
ENV DB_USER="postgres"
ENV DB_PASSWORD="postgres"
ENV REDIS_HOST="localhost"
ENV REDIS_PORT="6379"
ENV GRAPH_TRAVERSAL_URL="http://localhost:8080/rest/graph-traversal/shortest-path"

EXPOSE 8080

CMD ["sh", "-c", "java ${JAVA_OPTS} -jar ${PAYARA_HOME}/payara-micro.jar \
    --addLibs ${PAYARA_HOME}/postgresql.jar \
    --contextroot / \
    --deploy ${PAYARA_HOME}/cargo-tracker.war \
    --port 8080"]
