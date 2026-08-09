# Cargo Tracker Azure AKS Deployment Guide

## Overview

Cargo Tracker is a Jakarta EE 10 web application packaged as a WAR and built with Maven on Java 11. The container image uses a multi-stage Maven build and runs the WAR on Payara Micro using the configured HTTP port `8080`. Kubernetes probes use the application-native health endpoint at `/rest/health`.

## Prerequisites

- Docker Engine 24 or later
- Java 11 and Maven 3.9 for local development outside containers
- Azure CLI authenticated with `az login`
- kubectl installed and compatible with your AKS cluster
- Azure Container Registry or Docker Hub account
- An existing PostgreSQL-compatible database reachable from AKS
- Optional: Azure Application Gateway Ingress Controller installed for the provided ingress annotations

## Project Configuration Detected

- Build tool: Maven
- Packaging: WAR
- Java release: 11
- Framework: Jakarta EE 10 with JAX-RS, JSF, CDI, JPA, JMS and Payara Micro runtime
- Application port: 8080
- Health endpoint: `/rest/health`
- External configuration placeholders: `POSTGRESQL_JDBC_URL`, `POSTGRESQL_USERNAME`, `POSTGRESQL_PASSWORD`, `GRAPH_TRAVERSAL_URL`

## Local Docker Compose Run

1. Ensure a PostgreSQL database is available. This compose file intentionally starts only the application container.
2. Export or place the following values in a `.env` file:

```bash
POSTGRESQL_JDBC_URL=jdbc:postgresql://host.docker.internal:5432/postgres
POSTGRESQL_USERNAME=cargotracker
POSTGRESQL_PASSWORD=cargotracker
GRAPH_TRAVERSAL_URL=http://localhost:8080/rest/graph-traversal/shortest-path
```

3. Build and start the application:

```bash
docker compose up --build
```

4. Verify health:

```bash
curl http://localhost:8080/rest/health
```

## Build and Push an Image

Linux or macOS:

```bash
./scripts/build-push.sh
```

Windows:

```bat
scripts\build-push.bat
```

The scripts prompt for an image tag, registry type, registry credentials, and build-time database values used by Maven WAR filtering. Image names and tags are sanitized to lower-case Docker-compatible values. The Docker build context remains the repository root and wrapper scripts are not used.

## Azure AKS Setup

If you do not already have AKS and ACR, create them with commands similar to:

```bash
az group create --name rg-cargo-tracker --location eastus
az acr create --resource-group rg-cargo-tracker --name <uniqueAcrName> --sku Standard
az aks create --resource-group rg-cargo-tracker --name aks-cargo-tracker --node-count 2 --attach-acr <uniqueAcrName> --generate-ssh-keys
az aks get-credentials --resource-group rg-cargo-tracker --name aks-cargo-tracker
```

Install an ingress controller before applying ingress resources. For Application Gateway Ingress Controller, follow the official Azure AGIC installation documentation.

## Deploy to AKS

Linux or macOS:

```bash
./scripts/deploy-image.sh
```

Windows:

```bat
scripts\deploy-image.bat
```

The deployment scripts:

1. Prompt for Azure resource group and AKS cluster name.
2. Prompt for the full image URI, for example `myacr.azurecr.io/cargo-tracker:latest`.
3. Prompt for application-specific environment variables.
4. Configure kubectl with `az aks get-credentials`.
5. Replace Kubernetes manifest placeholders.
6. Apply namespace, deployment, service and ingress manifests.
7. Wait for rollout completion and list deployed resources.

## Kubernetes Manifests

- `kubernetes/namespace.yaml`: Creates namespace `cargo-tracker`.
- `kubernetes/deployment.yaml`: Runs two replicas with Java container resource limits and `/rest/health` probes.
- `kubernetes/service.yaml`: Exposes the application internally on port 80 targeting container port 8080.
- `kubernetes/ingress.yaml`: Routes external HTTP traffic to the service using Azure Application Gateway annotations.

## Scaling and Operations

Scale manually:

```bash
kubectl scale deployment/cargo-tracker -n cargo-tracker --replicas=3
```

Check rollout history:

```bash
kubectl rollout history deployment/cargo-tracker -n cargo-tracker
```

Rollback:

```bash
kubectl rollout undo deployment/cargo-tracker -n cargo-tracker
```

View logs:

```bash
kubectl logs -n cargo-tracker deployment/cargo-tracker
```

## Troubleshooting

- Pod image pull errors: verify the image URI, tag, and ACR permissions attached to AKS.
- Readiness probe failures: verify the application started and `/rest/health` returns JSON.
- Database connection errors: verify PostgreSQL network access from AKS and the JDBC URL embedded at build/deploy time.
- Ingress has no address: confirm an ingress controller is installed and watching the `cargo-tracker` namespace.
- Startup memory pressure: increase memory limits or reduce JVM heap via `JAVA_OPTS`.

## Security Considerations

- The image runs as a non-root user.
- Kubernetes security context disables privilege escalation and drops Linux capabilities.
- Do not commit production database passwords. Replace plain environment variables with Kubernetes Secrets for production.
- Use private ACR repositories and managed identities where possible.
- Enable TLS at the ingress layer and rotate database credentials regularly.

## Production Recommendations

- Store `POSTGRESQL_PASSWORD` in an AKS Secret and reference it with `valueFrom.secretKeyRef`.
- Use Azure Monitor Container Insights and structured application logging.
- Configure horizontal pod autoscaling after observing CPU and memory patterns.
- Use separate build-time profiles for each environment so WAR-filtered datasource configuration is deterministic.
