# Cargo Tracker Azure AKS Deployment Guide

## Overview

Cargo Tracker is a Jakarta EE 10 web application packaged as a WAR and run with Payara Micro. The application is built with Maven using Java 11 and exposes HTTP traffic on port 8080. The detected container health endpoint is `/rest/health`.

Generated artifacts include:

- `Dockerfile` for a multi-stage Maven build and Payara Micro runtime.
- `.dockerignore` for Java/Maven container builds.
- `docker-compose.yml` with only the application service.
- `scripts/build-push.sh` and `scripts/build-push.bat` for image build and push.
- `kubernetes/namespace.yaml`, `deployment.yaml`, `service.yaml`, and `ingress.yaml` for AKS.
- `scripts/deploy-image.sh` and `scripts/deploy-image.bat` for AKS deployment.

## Prerequisites

Install the following tools:

- Docker 24 or later.
- Azure CLI authenticated with `az login`.
- kubectl configured by Azure CLI.
- Access to an Azure Container Registry or Docker Hub repository.
- An AKS cluster with an ingress controller. The generated ingress uses Azure Application Gateway Ingress Controller annotations.
- An external PostgreSQL-compatible database for the cloud profile. No database container is generated because infrastructure services are intentionally external.

## Application Configuration

The application uses Maven profile `cloud` for production-style packaging. Important configuration values are:

| Variable | Purpose | Example |
| --- | --- | --- |
| `POSTGRESQL_JDBC_URL` | PostgreSQL JDBC URL embedded into the WAR at build time and prompted during deployment | `jdbc:postgresql://mydb.postgres.database.azure.com:5432/postgres` |
| `POSTGRESQL_USERNAME` | Database user | `cargotracker` |
| `POSTGRESQL_PASSWORD` | Database password. Deployment script stores this in `cargo-tracker-secrets` | `change-me` |
| `GRAPH_TRAVERSAL_URL` | Internal REST URL for route calculation | `http://localhost:8080/rest/graph-traversal/shortest-path` |
| `JAVA_OPTS` | JVM memory and container tuning | `-Xms256m -Xmx512m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0` |

Because this Jakarta EE application defines JDBC resources in `WEB-INF/web.xml`, database settings are Maven filtered into the WAR during build. Rebuild the image when changing JDBC connection details unless the application is refactored to read those values dynamically at runtime.

## Local Docker Usage

1. Ensure an external PostgreSQL database is reachable.
2. Export configuration values, for example:

```bash
export POSTGRESQL_JDBC_URL="jdbc:postgresql://host.docker.internal:5432/postgres"
export POSTGRESQL_USERNAME="postgres"
export POSTGRESQL_PASSWORD="postgres"
export GRAPH_TRAVERSAL_URL="http://localhost:8080/rest/graph-traversal/shortest-path"
```

3. Start the application container:

```bash
docker compose up --build
```

4. Verify health:

```bash
curl http://localhost:8080/rest/health
```

The compose file contains only one service: `cargo-tracker`. It does not start PostgreSQL, Kafka, Redis, or any other infrastructure dependency.

## Build and Push Image

Linux or macOS:

```bash
chmod +x scripts/build-push.sh
./scripts/build-push.sh
```

Windows:

```cmd
scripts\build-push.bat
```

The scripts prompt for:

1. Image tag, defaulting to `latest`.
2. Registry type: Azure Container Registry or Docker Hub.
3. Registry-specific authentication details.

The image name is sanitized to `cargo-tracker` and the final image URI is printed after push.

## Azure AKS Setup

If you need to create an example AKS cluster with ACR integration:

```bash
az group create --name rg-cargo-tracker --location eastus
az acr create --resource-group rg-cargo-tracker --name mycargotrackeracr --sku Standard
az aks create \
  --resource-group rg-cargo-tracker \
  --name aks-cargo-tracker \
  --node-count 2 \
  --enable-managed-identity \
  --attach-acr mycargotrackeracr \
  --generate-ssh-keys
az aks get-credentials --resource-group rg-cargo-tracker --name aks-cargo-tracker
```

For Application Gateway Ingress Controller, enable the AGIC add-on or install the ingress controller according to your Azure network topology.

## Deploy to AKS

Linux or macOS:

```bash
chmod +x scripts/deploy-image.sh
./scripts/deploy-image.sh
```

Windows:

```cmd
scripts\deploy-image.bat
```

The scripts prompt for:

- Azure resource group.
- AKS cluster name.
- Full image URI, such as `myregistry.azurecr.io/cargo-tracker:latest`.
- PostgreSQL JDBC URL, username, and password.
- Graph traversal URL.

The deployment script performs these steps:

1. Copies manifests to a temporary directory.
2. Replaces placeholders such as `{{IMAGE_URI}}`, `{{POSTGRESQL_JDBC_URL}}`, `{{POSTGRESQL_USERNAME}}`, and `{{GRAPH_TRAVERSAL_URL}}`.
3. Configures kubectl using `az aks get-credentials`.
4. Applies namespace, optional secret, deployment, service, and ingress.
5. Waits for rollout and prints pods, services, and ingress details.

## Kubernetes Resources

- Namespace: `cargo-tracker`
- Deployment: `cargo-tracker`, 2 replicas, rolling update strategy.
- Service: `cargo-tracker-service`, ClusterIP on port 80 targeting container port 8080.
- Ingress: `cargo-tracker-ingress`, host `cargo-tracker.example.com`.
- Health probes: HTTP GET `/rest/health`.
- Resource requests: CPU `250m`, memory `512Mi`.
- Resource limits: CPU `500m`, memory `1Gi`.

Update the ingress host to your production DNS name before a long-term deployment.

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
kubectl logs -n cargo-tracker deployment/cargo-tracker --tail=200
```

## Security Considerations

- The container runs as a non-root user.
- Kubernetes security context disables privilege escalation and drops Linux capabilities.
- Database passwords should be provided through Kubernetes secrets or a production secret manager such as Azure Key Vault with the Secrets Store CSI Driver.
- Use private ACR repositories and AKS managed identity for image pull access.
- Enable TLS at ingress using Application Gateway certificates or cert-manager.
- Restrict database network access to AKS outbound IPs or private networking.

## Troubleshooting

### Pods are not ready

```bash
kubectl describe pod -n cargo-tracker -l app=cargo-tracker
kubectl logs -n cargo-tracker deployment/cargo-tracker
```

Common causes include an unreachable database, incorrect image URI, or a slow Payara startup. The startup probe allows up to approximately three minutes before liveness checks restart the container.

### Image pull failures

Verify AKS can access the registry:

```bash
az aks update --name aks-cargo-tracker --resource-group rg-cargo-tracker --attach-acr mycargotrackeracr
kubectl describe pod -n cargo-tracker -l app=cargo-tracker
```

### Ingress does not route traffic

Check ingress status and AGIC logs:

```bash
kubectl get ingress -n cargo-tracker
kubectl describe ingress cargo-tracker-ingress -n cargo-tracker
```

Ensure DNS for your host points to the Application Gateway frontend IP.

### Database connection errors

Validate the JDBC URL, username, password secret, firewall rules, and TLS requirements for your database service. Rebuild the image with the correct Maven cloud profile properties if database values were embedded incorrectly during package creation.
