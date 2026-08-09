# Cargo Tracker Deployment Guide for Azure AKS

## Overview

This project is a Java 11 Jakarta EE Cargo Tracker web application packaged as a WAR. The container image builds the WAR with Maven and runs it on Payara Micro. The generated Kubernetes manifests deploy only the application container to Azure Kubernetes Service (AKS); databases, queues, and other infrastructure are expected to be provided separately.

## Detected application characteristics

- Build tool: Maven
- Java version: 11 (`maven.compiler.release`)
- Packaging: WAR (`target/cargo-tracker.war`)
- Runtime: Payara Micro for Jakarta EE 10
- Application port: 8080
- Health endpoint: `/rest/health`
- External configuration detected:
  - `POSTGRESQL_JDBC_URL`
  - `POSTGRESQL_USERNAME`
  - `POSTGRESQL_PASSWORD`

## Prerequisites

Install the following tools:

- Docker Engine or Docker Desktop
- Azure CLI (`az`)
- kubectl
- Access to an Azure subscription
- An AKS cluster
- A container registry, preferably Azure Container Registry (ACR)
- An external PostgreSQL-compatible database for cloud deployments

## Local Docker Compose deployment

1. Create an optional `.env` file in the repository root:

   ```properties
   POSTGRESQL_JDBC_URL=jdbc:postgresql://host.docker.internal:5432/postgres
   POSTGRESQL_USERNAME=postgres
   POSTGRESQL_PASSWORD=postgres
   ```

2. Build and run the application:

   ```bash
   docker compose up --build
   ```

3. Open the application:

   - Web UI: http://localhost:8080/
   - Health: http://localhost:8080/rest/health

The compose file contains only the application service. It does not start PostgreSQL, JMS brokers, Redis, Kafka, or any other infrastructure component.

## Build and push image

### Linux or macOS

```bash
chmod +x scripts/build-push.sh
./scripts/build-push.sh
```

The script prompts for:

1. Image tag, defaulting to `latest`
2. Build-time PostgreSQL JDBC values used by the Maven `cloud` profile
3. Registry type:
   - Azure Container Registry
   - Docker Hub

For ACR, ensure you are signed in first:

```bash
az login
az acr login --name <acr-name>
```

### Windows

```bat
scripts\build-push.bat
```

The Windows script provides the same prompts and builds from the repository root.

## Azure AKS setup

If an AKS cluster and ACR are already available, skip to deployment. To create a minimal setup:

```bash
az login
az group create --name rg-cargo-tracker --location eastus
az acr create --resource-group rg-cargo-tracker --name <uniqueAcrName> --sku Standard
az aks create \
  --resource-group rg-cargo-tracker \
  --name aks-cargo-tracker \
  --node-count 2 \
  --enable-managed-identity \
  --attach-acr <uniqueAcrName> \
  --generate-ssh-keys
az aks get-credentials --resource-group rg-cargo-tracker --name aks-cargo-tracker
```

For ingress with Azure Application Gateway Ingress Controller, install and configure AGIC according to your AKS networking model. The provided ingress uses:

```yaml
kubernetes.io/ingress.class: azure/application-gateway
```

Adjust `kubernetes/ingress.yaml` host `cargo-tracker.example.com` to your DNS name.

## Deploy to AKS

### Linux or macOS

```bash
chmod +x scripts/deploy-image.sh
./scripts/deploy-image.sh
```

The script prompts for:

- Azure resource group
- AKS cluster name
- Full image URI with tag, such as `myacr.azurecr.io/cargo-tracker:latest`
- PostgreSQL connection values

It then:

1. Copies manifests to a temporary directory
2. Replaces placeholders such as `{{IMAGE_URI}}`
3. Configures kubectl for AKS
4. Applies namespace, deployment, service, and ingress
5. Waits for rollout
6. Prints pods, services, and ingress resources

### Windows

```bat
scripts\deploy-image.bat
```

## Kubernetes manifest details

- `kubernetes/namespace.yaml`: creates namespace `cargo-tracker`
- `kubernetes/deployment.yaml`: deploys 2 replicas with JVM resource requests and limits
- `kubernetes/service.yaml`: exposes the container on port 80 inside the cluster, targeting container port 8080
- `kubernetes/ingress.yaml`: defines an Azure Application Gateway ingress route

## Configuration management

Database configuration is compiled into the WAR by the Maven `cloud` profile. Rebuild the image when database JDBC URL, username, or password values change. The Kubernetes deployment also sets the same variables as pod environment variables for visibility and for future runtime configuration extensions.

For production, prefer Kubernetes Secrets for sensitive values:

```bash
kubectl create secret generic cargo-tracker-db \
  --namespace cargo-tracker \
  --from-literal=POSTGRESQL_USERNAME=postgres \
  --from-literal=POSTGRESQL_PASSWORD='<password>'
```

Then update the deployment to source values from `secretKeyRef` instead of plain environment variable values.

## Health checks

The application exposes a Jakarta REST health endpoint at:

```text
/rest/health
```

The Kubernetes liveness, readiness, and startup probes use this endpoint. The Dockerfile intentionally does not define a Docker HEALTHCHECK and does not install curl or wget; Kubernetes handles health probing.

## Scaling and operations

Scale manually:

```bash
kubectl scale deployment cargo-tracker --replicas=3 -n cargo-tracker
```

View rollout history:

```bash
kubectl rollout history deployment/cargo-tracker -n cargo-tracker
```

Rollback:

```bash
kubectl rollout undo deployment/cargo-tracker -n cargo-tracker
```

Inspect logs:

```bash
kubectl logs -l app=cargo-tracker -n cargo-tracker --tail=200
```

## Troubleshooting

### Pods do not start

```bash
kubectl describe pod -l app=cargo-tracker -n cargo-tracker
kubectl logs -l app=cargo-tracker -n cargo-tracker
```

Common causes:

- Image URI is incorrect or image pull permissions are missing
- Database host is unreachable from AKS
- Database credentials were incorrect at image build time
- Application startup exceeds probe thresholds

### Image pull errors

If using ACR, attach the registry to AKS:

```bash
az aks update --resource-group <resource-group> --name <cluster-name> --attach-acr <acr-name>
```

### Ingress not working

Check ingress and AGIC status:

```bash
kubectl describe ingress cargo-tracker-ingress -n cargo-tracker
kubectl get ingress -n cargo-tracker
kubectl logs -n kube-system -l app=ingress-appgw
```

Ensure DNS points to the Application Gateway public IP and that AGIC is installed.

## Security considerations

- The runtime container uses a non-root user.
- Kubernetes security context disables privilege escalation and drops Linux capabilities.
- Do not commit database passwords to source control.
- Use ACR private repositories and AKS-managed identity integration.
- Restrict database network access to AKS outbound addresses or private networking.
- Enable TLS on ingress for production.

## Java runtime tuning

Default JVM settings are:

```text
-Xms256m -Xmx512m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Dfile.encoding=UTF-8 -Djava.awt.headless=true
```

Adjust `JAVA_OPTS` if memory limits change. Keep heap size below the Kubernetes memory limit to allow native memory, metaspace, threads, and Payara overhead.
