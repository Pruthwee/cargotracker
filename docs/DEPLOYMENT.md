# Cargo Tracker - AWS EKS Deployment Guide

## Overview

This guide covers building, containerizing, and deploying the **Eclipse Cargo Tracker** application to **AWS Elastic Kubernetes Service (EKS)**.

- **Framework**: Jakarta EE 10 (Payara Micro runtime)
- **Build Tool**: Maven 3.9.x
- **Java Version**: 11 (Amazon Corretto 11 runtime)
- **Package Type**: WAR
- **Application Port**: 8080
- **Health Endpoint**: `GET /rest/health`
- **Context Root**: `/`

---

## Prerequisites

### Local Development
- Docker Desktop 24+
- Java 11 (Amazon Corretto or Eclipse Temurin)
- Maven 3.9+
- Node.js 20+ (for CSS/HTML minification build stages)

### AWS EKS Deployment
- AWS CLI v2 configured (`aws configure`)
- `kubectl` 1.28+
- `eksctl` (optional, for cluster creation)
- IAM permissions: `ecr:*`, `eks:*`, `ec2:*`, `iam:PassRole`
- AWS Load Balancer Controller installed on the EKS cluster

---

## Project Structure

```
cargo-tracker-2/
├── Dockerfile                  # Multi-stage build (CSS minify → HTML minify → Maven build → Runtime)
├── docker-compose.yml          # Local development (application only)
├── .dockerignore               # Excludes target/, mvnw, .mvn/, test files
├── pom.xml                     # Maven build descriptor (Java 11, WAR packaging)
├── post-boot-commands.asadmin  # Payara post-boot configuration
├── kubernetes/
│   ├── namespace.yaml          # Kubernetes namespace: cargo-tracker
│   ├── deployment.yaml         # Deployment with 2 replicas, health probes
│   ├── service.yaml            # ClusterIP service on port 80 → 8080
│   └── ingress.yaml            # AWS ALB Ingress
├── scripts/
│   ├── build-push.sh           # Linux/macOS: build & push to ECR or Docker Hub
│   ├── build-push.bat          # Windows: build & push to ECR or Docker Hub
│   ├── deploy-image.sh         # Linux/macOS: deploy to AWS EKS
│   └── deploy-image.bat        # Windows: deploy to AWS EKS
└── docs/
    └── DEPLOYMENT.md           # This file
```

---

## Environment Variables

| Variable | Description | Default |
|---|---|---|
| `DB_JDBC_URL` | PostgreSQL JDBC connection URL | `jdbc:postgresql://localhost:5432/postgres` |
| `DB_USER` | PostgreSQL username | `postgres` |
| `DB_PASSWORD` | PostgreSQL password | `postgres` |
| `REDIS_HOST` | Amazon ElastiCache (Redis) host | `localhost` |
| `REDIS_PORT` | Redis port | `6379` |
| `GRAPH_TRAVERSAL_URL` | Internal graph traversal REST endpoint | `http://localhost:8080/rest/graph-traversal/shortest-path` |
| `JAVA_OPTS` | JVM options | `-Xmx512m -Xms256m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0` |
| `TZ` | Timezone | `UTC` |

---

## Local Development with Docker Compose

### 1. Configure environment variables

Create a `.env` file in the project root:

```env
DB_JDBC_URL=jdbc:postgresql://host.docker.internal:5432/postgres
DB_USER=postgres
DB_PASSWORD=yourpassword
REDIS_HOST=host.docker.internal
REDIS_PORT=6379
GRAPH_TRAVERSAL_URL=http://localhost:8080/rest/graph-traversal/shortest-path
```

### 2. Build and start the application

```bash
# Build the Docker image
docker build -t cargo-tracker:latest .

# Start the application container
docker-compose up -d

# View logs
docker-compose logs -f cargo-tracker
```

### 3. Verify the application

```bash
# Health check
curl http://localhost:8080/rest/health

# Expected response:
# {"status":"UP","application":"cargo-tracker"}

# Access the web UI
open http://localhost:8080
```

### 4. Stop the application

```bash
docker-compose down
```

---

## Building and Pushing the Docker Image

### Linux / macOS

```bash
chmod +x scripts/build-push.sh
./scripts/build-push.sh
```

The script will prompt you to:
1. Enter an image tag (default: `latest`)
2. Select registry type: **1. AWS ECR** or **2. Docker Hub**
3. Provide registry credentials

### Windows

```cmd
scripts\build-push.bat
```

### Manual Build (AWS ECR)

```bash
AWS_REGION=us-east-1
AWS_ACCOUNT_ID=123456789012
IMAGE_TAG=latest
ECR_REPO=cargo-tracker
REGISTRY_URL="${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"

# Authenticate
aws ecr get-login-password --region $AWS_REGION | \
  docker login --username AWS --password-stdin $REGISTRY_URL

# Create repository (if not exists)
aws ecr create-repository --repository-name $ECR_REPO --region $AWS_REGION 2>/dev/null || true

# Build and push
docker build -t "${REGISTRY_URL}/${ECR_REPO}:${IMAGE_TAG}" .
docker push "${REGISTRY_URL}/${ECR_REPO}:${IMAGE_TAG}"
```

---

## AWS EKS Deployment

### Prerequisites

#### 1. Install AWS Load Balancer Controller

The ingress manifest uses the AWS ALB Ingress Controller. Install it on your EKS cluster:

```bash
# Add the EKS chart repository
helm repo add eks https://aws.github.io/eks-charts
helm repo update

# Install the controller
helm install aws-load-balancer-controller eks/aws-load-balancer-controller \
  -n kube-system \
  --set clusterName=<YOUR_CLUSTER_NAME> \
  --set serviceAccount.create=false \
  --set serviceAccount.name=aws-load-balancer-controller
```

#### 2. Configure kubectl

```bash
aws eks update-kubeconfig --region us-east-1 --name <YOUR_CLUSTER_NAME>
kubectl cluster-info
```

### Deploy Using Scripts

#### Linux / macOS

```bash
chmod +x scripts/deploy-image.sh
./scripts/deploy-image.sh
```

#### Windows

```cmd
scripts\deploy-image.bat
```

The script will prompt for:
- AWS Region
- EKS Cluster Name
- Docker image URI (full path with tag)
- Application environment variables (DB_JDBC_URL, DB_USER, DB_PASSWORD, REDIS_HOST, REDIS_PORT, GRAPH_TRAVERSAL_URL)

### Manual Deployment

```bash
# 1. Set your image URI
IMAGE_URI="123456789012.dkr.ecr.us-east-1.amazonaws.com/cargo-tracker:latest"

# 2. Update the deployment manifest
sed -i 's|{{IMAGE_URI}}|'"$IMAGE_URI"'|g'                                    kubernetes/deployment.yaml
sed -i 's|{{DB_JDBC_URL}}|jdbc:postgresql://your-db-host:5432/postgres|g'   kubernetes/deployment.yaml
sed -i 's|{{DB_USER}}|postgres|g'                                            kubernetes/deployment.yaml
sed -i 's|{{DB_PASSWORD}}|yourpassword|g'                                    kubernetes/deployment.yaml
sed -i 's|{{REDIS_HOST}}|your-elasticache-host|g'                           kubernetes/deployment.yaml
sed -i 's|{{REDIS_PORT}}|6379|g'                                             kubernetes/deployment.yaml
sed -i 's|{{GRAPH_TRAVERSAL_URL}}|http://localhost:8080/rest/graph-traversal/shortest-path|g' kubernetes/deployment.yaml

# 3. Apply manifests in order
kubectl apply -f kubernetes/namespace.yaml
kubectl apply -f kubernetes/deployment.yaml
kubectl apply -f kubernetes/service.yaml
kubectl apply -f kubernetes/ingress.yaml

# 4. Wait for rollout
kubectl rollout status deployment/cargo-tracker -n cargo-tracker --timeout=300s

# 5. Verify
kubectl get pods,svc,ingress -n cargo-tracker
```

---

## Kubernetes Manifest Details

### Namespace (`kubernetes/namespace.yaml`)
Creates the `cargo-tracker` namespace to isolate all resources.

### Deployment (`kubernetes/deployment.yaml`)
- **Replicas**: 2 (high availability)
- **Image**: Parameterized via `{{IMAGE_URI}}` placeholder
- **Resources**: requests: 250m CPU / 512Mi RAM; limits: 500m CPU / 1Gi RAM
- **Liveness Probe**: `GET /rest/health` on port 8080, initial delay 90s (JVM + Payara startup)
- **Readiness Probe**: `GET /rest/health` on port 8080, initial delay 60s
- **Graceful Shutdown**: `terminationGracePeriodSeconds: 60`

### Service (`kubernetes/service.yaml`)
- **Type**: ClusterIP (internal only, exposed via Ingress)
- **Port mapping**: 80 → 8080

### Ingress (`kubernetes/ingress.yaml`)
- **Controller**: AWS ALB (Application Load Balancer)
- **Scheme**: internet-facing
- **Health check path**: `/rest/health`
- **Host**: `cargo-tracker.example.com` (update to your actual domain)

---

## Scaling and Management

### Horizontal Pod Autoscaler (HPA)

```bash
kubectl autoscale deployment cargo-tracker \
  --cpu-percent=70 \
  --min=2 \
  --max=10 \
  -n cargo-tracker
```

### Rolling Update

```bash
# Update image
kubectl set image deployment/cargo-tracker \
  cargo-tracker=<NEW_IMAGE_URI> \
  -n cargo-tracker

# Monitor rollout
kubectl rollout status deployment/cargo-tracker -n cargo-tracker
```

### Rollback

```bash
# Rollback to previous version
kubectl rollout undo deployment/cargo-tracker -n cargo-tracker

# Rollback to specific revision
kubectl rollout history deployment/cargo-tracker -n cargo-tracker
kubectl rollout undo deployment/cargo-tracker --to-revision=2 -n cargo-tracker
```

---

## Troubleshooting

### Pod not starting

```bash
# Check pod status
kubectl get pods -n cargo-tracker

# Describe pod for events
kubectl describe pod <POD_NAME> -n cargo-tracker

# View logs
kubectl logs <POD_NAME> -n cargo-tracker
kubectl logs -l app=cargo-tracker -n cargo-tracker --tail=100
```

### Health check failing

The application exposes `GET /rest/health` which returns:
```json
{"status":"UP","application":"cargo-tracker"}
```

Payara Micro takes 60-90 seconds to start. If probes fail:
```bash
# Temporarily increase initialDelaySeconds in deployment.yaml
# Then re-apply:
kubectl apply -f kubernetes/deployment.yaml
```

### Database connection issues

```bash
# Verify DB_JDBC_URL is correct
kubectl exec -it <POD_NAME> -n cargo-tracker -- env | grep DB_

# Test connectivity from pod (if netcat available)
kubectl exec -it <POD_NAME> -n cargo-tracker -- sh -c "nc -zv <DB_HOST> 5432"
```

### Redis connection issues

```bash
# Verify REDIS_HOST and REDIS_PORT
kubectl exec -it <POD_NAME> -n cargo-tracker -- env | grep REDIS_
```

### Ingress not getting an address

```bash
# Check ALB controller logs
kubectl logs -n kube-system -l app.kubernetes.io/name=aws-load-balancer-controller

# Check ingress events
kubectl describe ingress cargo-tracker-ingress -n cargo-tracker
```

---

## Security Considerations

1. **Non-root container**: The application runs as the `payara` user (non-root).
2. **Secrets management**: Use Kubernetes Secrets or AWS Secrets Manager for `DB_PASSWORD` instead of plain environment variables.
3. **Network policies**: Consider adding Kubernetes NetworkPolicy to restrict pod-to-pod communication.
4. **Image scanning**: Enable ECR image scanning to detect vulnerabilities.
5. **IRSA (IAM Roles for Service Accounts)**: Use IRSA for fine-grained AWS permissions instead of node-level IAM roles.

### Using Kubernetes Secrets for credentials

```bash
kubectl create secret generic cargo-tracker-secrets \
  --from-literal=DB_PASSWORD=yourpassword \
  -n cargo-tracker
```

Then reference in `deployment.yaml`:
```yaml
- name: DB_PASSWORD
  valueFrom:
    secretKeyRef:
      name: cargo-tracker-secrets
      key: DB_PASSWORD
```

---

## Jakarta EE / Payara Micro Notes

- The application is packaged as a **WAR** and deployed to **Payara Micro** (embedded Jakarta EE container).
- The `post-boot-commands.asadmin` file configures Payara at startup (adds PostgreSQL driver, deploys WAR with context root `/`).
- The **cloud** Maven profile (`-Pcloud`) is used for Docker builds — it includes the PostgreSQL JDBC driver and configures the data source for PostgreSQL.
- JVM options are set via the `JAVA_OPTS` environment variable: `-Xmx512m -Xms256m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0`.
- **Amazon Corretto 11** is used as the runtime base image (explicit base image parameter).

---

## Quick Reference

```bash
# Build image
docker build -t cargo-tracker:latest .

# Run locally
docker-compose up -d

# Push to ECR
./scripts/build-push.sh

# Deploy to EKS
./scripts/deploy-image.sh

# Check deployment
kubectl get pods,svc,ingress -n cargo-tracker

# View logs
kubectl logs -l app=cargo-tracker -n cargo-tracker -f

# Rollback
kubectl rollout undo deployment/cargo-tracker -n cargo-tracker
```
