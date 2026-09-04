# Cargo Tracker - Deployment Guide (AWS EKS)

## Overview

This guide covers building, pushing, and deploying the **Eclipse Cargo Tracker** Jakarta EE 10 application to **AWS Elastic Kubernetes Service (EKS)**.

- **Framework**: Jakarta EE 10 (CDI, JPA, JAX-RS, JSF/PrimeFaces, JMS, Batch)
- **Runtime**: Payara Server Full 6.2025.3
- **Build Tool**: Maven 3.9.x
- **Java Version**: 11
- **Packaging**: WAR
- **Application Port**: 8080 (HTTP), 8081 (HTTPS)
- **Explicit Base Image**: eclipse-temurin:11-jdk (runtime stage)

---

## Prerequisites

### Local Development
- Docker Desktop 24.x or later
- Java 11 (eclipse-temurin:11-jdk recommended)
- Maven 3.9.x
- Node.js 20.x (for CSS/HTML minification build stages)

### AWS EKS Deployment
- AWS CLI v2 configured with appropriate IAM permissions
- `kubectl` v1.28 or later
- `eksctl` (optional, for cluster creation)
- AWS IAM permissions:
  - `ecr:GetAuthorizationToken`, `ecr:BatchCheckLayerAvailability`, `ecr:PutImage`
  - `eks:DescribeCluster`, `eks:ListClusters`
  - `elasticache:DescribeCacheClusters` (if using ElastiCache Redis)
  - `rds:DescribeDBInstances` (if using RDS PostgreSQL)

---

## Project Structure

```
cargo-tracker-1/
├── Dockerfile                  # Multi-stage build (CSS minify → HTML minify → Maven build → Payara runtime)
├── docker-compose.yml          # Local development (application only)
├── .dockerignore               # Excludes wrapper files, target/, test sources
├── pom.xml                     # Maven build descriptor
├── post-boot-commands.asadmin  # Payara post-boot deployment commands
├── package.json                # Node.js tooling for CSS/HTML minification
├── postcss.config.js           # PostCSS/cssnano configuration
├── kubernetes/
│   ├── namespace.yaml          # Kubernetes namespace: cargo-tracker
│   ├── deployment.yaml         # Deployment with 2 replicas, resource limits, probes
│   ├── service.yaml            # ClusterIP service (port 80 → 8080)
│   └── ingress.yaml            # AWS ALB Ingress Controller configuration
├── scripts/
│   ├── build-push.sh           # Linux/macOS: build & push to ECR or Docker Hub
│   ├── build-push.bat          # Windows: build & push to ECR or Docker Hub
│   ├── deploy-image.sh         # Linux/macOS: deploy to AWS EKS
│   └── deploy-image.bat        # Windows: deploy to AWS EKS
└── docs/
    └── DEPLOYMENT.md           # This file
```

---

## Local Development with Docker Compose

### 1. Configure Environment Variables

Create a `.env` file in the project root:

```env
POSTGRES_JDBC_URL=jdbc:postgresql://your-db-host:5432/cargotracker
POSTGRES_USERNAME=postgres
POSTGRES_PASSWORD=your-password
GRAPH_TRAVERSAL_URL=http://localhost:8080/rest/graph-traversal/shortest-path
REDIS_HOST=your-redis-host
REDIS_PORT=6379
```

### 2. Build and Start

```bash
# Build and start the application container
docker-compose up --build

# Run in background
docker-compose up -d --build

# View logs
docker-compose logs -f cargo-tracker

# Stop
docker-compose down
```

### 3. Access the Application

- **Application**: http://localhost:8080
- **Admin Console**: http://localhost:4848 (Payara Admin)

---

## Building and Pushing the Docker Image

### Linux / macOS

```bash
chmod +x scripts/build-push.sh
./scripts/build-push.sh
```

### Windows

```cmd
scripts\build-push.bat
```

The script will prompt you to:
1. Enter an image tag (defaults to `latest`)
2. Select registry type: **AWS ECR** or **Docker Hub**
3. Provide registry credentials and repository details

The script automatically:
- Sanitizes the image name to lowercase with hyphens
- Creates the ECR repository if it does not exist (ECR only)
- Builds the Docker image from the project root
- Pushes the image to the selected registry

---

## AWS EKS Deployment

### Step 1: Set Up EKS Cluster (if not already created)

```bash
# Create a new EKS cluster (optional)
eksctl create cluster \
  --name cargo-tracker-cluster \
  --region us-east-1 \
  --nodegroup-name standard-workers \
  --node-type t3.medium \
  --nodes 2 \
  --nodes-min 1 \
  --nodes-max 4 \
  --managed

# Configure kubectl
aws eks update-kubeconfig --region us-east-1 --name cargo-tracker-cluster
```

### Step 2: Install AWS Load Balancer Controller

The ingress manifest uses the AWS ALB Ingress Controller. Install it on your cluster:

```bash
# Add the EKS chart repository
helm repo add eks https://aws.github.io/eks-charts
helm repo update

# Install the AWS Load Balancer Controller
helm install aws-load-balancer-controller eks/aws-load-balancer-controller \
  -n kube-system \
  --set clusterName=cargo-tracker-cluster \
  --set serviceAccount.create=false \
  --set serviceAccount.name=aws-load-balancer-controller
```

> **Note**: The AWS Load Balancer Controller requires an IAM role with appropriate permissions. See [AWS documentation](https://docs.aws.amazon.com/eks/latest/userguide/aws-load-balancer-controller.html) for setup details.

### Step 3: Build and Push the Image

```bash
./scripts/build-push.sh
# Follow prompts to push to AWS ECR
```

### Step 4: Deploy to EKS

#### Linux / macOS

```bash
chmod +x scripts/deploy-image.sh
./scripts/deploy-image.sh
```

#### Windows

```cmd
scripts\deploy-image.bat
```

The deploy script will prompt for:
- AWS Region
- EKS Cluster Name
- Full Docker image URI (e.g., `123456789.dkr.ecr.us-east-1.amazonaws.com/cargo-tracker:latest`)
- PostgreSQL JDBC URL, username, and password
- Graph Traversal URL
- Redis host and port

The script will:
1. Configure `kubectl` for your EKS cluster
2. Verify cluster connectivity
3. Replace all `{{PLACEHOLDER}}` values in `kubernetes/deployment.yaml`
4. Apply manifests in order: namespace → deployment → service → ingress
5. Wait for the deployment rollout to complete
6. Display the application URL from the ingress

### Step 5: Verify Deployment

```bash
# Check all resources in the namespace
kubectl get all -n cargo-tracker

# Check pod logs
kubectl logs -l app=cargo-tracker -n cargo-tracker --tail=100

# Check ingress and get the ALB hostname
kubectl get ingress cargo-tracker-ingress -n cargo-tracker

# Describe a pod for detailed status
kubectl describe pod -l app=cargo-tracker -n cargo-tracker
```

---

## Kubernetes Manifest Details

### namespace.yaml
Creates the `cargo-tracker` namespace to isolate all application resources.

### deployment.yaml
- **Replicas**: 2 (for high availability)
- **Image**: Pulled from `{{IMAGE_URI}}` (replaced at deploy time)
- **Resources**:
  - Requests: `cpu: 250m`, `memory: 512Mi`
  - Limits: `cpu: 500m`, `memory: 1Gi`
- **Probes**: TCP socket probes on port 8080 (Payara starts slowly; initial delay is 90s for liveness, 60s for readiness)
- **Environment Variables**: PostgreSQL, Redis, and Graph Traversal URL injected at deploy time

### service.yaml
- **Type**: ClusterIP
- **Port mapping**: 80 → 8080 (HTTP), 443 → 8081 (HTTPS)

### ingress.yaml
- **Controller**: AWS ALB (Application Load Balancer)
- **Scheme**: internet-facing
- **Target type**: IP (direct pod routing)
- **Session affinity**: Cookie-based (required for JSF stateful sessions)
- **Host**: `cargo-tracker.example.com` — update to your actual domain

---

## Configuration Management

### Environment Variables Reference

| Variable | Description | Example |
|---|---|---|
| `POSTGRES_JDBC_URL` | PostgreSQL JDBC connection URL | `jdbc:postgresql://host:5432/cargotracker` |
| `POSTGRES_USERNAME` | PostgreSQL username | `postgres` |
| `POSTGRES_PASSWORD` | PostgreSQL password | `secret` |
| `GRAPH_TRAVERSAL_URL` | Internal graph traversal REST endpoint | `http://localhost:8080/rest/graph-traversal/shortest-path` |
| `REDIS_HOST` | Redis/ElastiCache hostname | `my-cluster.cache.amazonaws.com` |
| `REDIS_PORT` | Redis port | `6379` |
| `TZ` | Timezone | `UTC` |

### Using Kubernetes Secrets for Sensitive Values

For production, store sensitive values in Kubernetes Secrets:

```bash
kubectl create secret generic cargo-tracker-secrets \
  --from-literal=POSTGRES_PASSWORD=your-password \
  --from-literal=POSTGRES_USERNAME=postgres \
  -n cargo-tracker
```

Then reference in `deployment.yaml`:

```yaml
env:
  - name: POSTGRES_PASSWORD
    valueFrom:
      secretKeyRef:
        name: cargo-tracker-secrets
        key: POSTGRES_PASSWORD
```

---

## Scaling and Management

### Horizontal Pod Autoscaler

```bash
kubectl autoscale deployment cargo-tracker \
  --cpu-percent=70 \
  --min=2 \
  --max=10 \
  -n cargo-tracker
```

### Rolling Update

```bash
# Update the image
kubectl set image deployment/cargo-tracker \
  cargo-tracker=123456789.dkr.ecr.us-east-1.amazonaws.com/cargo-tracker:v2.0 \
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

### Pod Not Starting

```bash
# Check pod events
kubectl describe pod -l app=cargo-tracker -n cargo-tracker

# Check logs
kubectl logs -l app=cargo-tracker -n cargo-tracker --previous
```

**Common causes**:
- Payara takes 60-90 seconds to start. Increase `initialDelaySeconds` in probes if needed.
- Missing or incorrect database connection details.
- Insufficient memory — increase `resources.limits.memory` to `2Gi` if OOMKilled.

### Database Connection Issues

Verify the PostgreSQL JDBC URL is reachable from within the cluster:

```bash
kubectl run -it --rm debug --image=eclipse-temurin:11-jre-alpine --restart=Never -n cargo-tracker -- \
  sh -c "nc -zv your-db-host 5432"
```

### Redis Connection Issues

```bash
kubectl run -it --rm debug --image=redis:alpine --restart=Never -n cargo-tracker -- \
  redis-cli -h your-redis-host -p 6379 ping
```

### Ingress / ALB Not Provisioning

```bash
# Check ALB controller logs
kubectl logs -n kube-system -l app.kubernetes.io/name=aws-load-balancer-controller

# Check ingress events
kubectl describe ingress cargo-tracker-ingress -n cargo-tracker
```

Ensure the AWS Load Balancer Controller is installed and the IAM role has the required permissions.

### Image Pull Errors

```bash
# Verify ECR credentials
aws ecr get-login-password --region us-east-1 | \
  docker login --username AWS --password-stdin \
  123456789.dkr.ecr.us-east-1.amazonaws.com

# Check if image exists
aws ecr describe-images --repository-name cargo-tracker --region us-east-1
```

---

## Security Considerations

1. **Secrets Management**: Use AWS Secrets Manager or Kubernetes Secrets for database passwords and API keys. Never hardcode credentials in manifests.
2. **IRSA (IAM Roles for Service Accounts)**: Use IRSA to grant the application pod access to AWS services (ElastiCache, RDS) without static credentials.
3. **Network Policies**: Restrict pod-to-pod communication using Kubernetes NetworkPolicy.
4. **Image Scanning**: Enable ECR image scanning to detect vulnerabilities before deployment.
5. **TLS**: Configure ACM certificates in the ALB ingress annotations for HTTPS termination.
6. **Non-root User**: The Payara base image runs as a non-root user by default. Do not override `runAsUser` unless required.
7. **Resource Limits**: Always set resource limits to prevent noisy-neighbor issues in shared clusters.

---

## Jakarta EE / Payara-Specific Notes

- **Payara Startup Time**: Payara Server Full takes 60-90 seconds to fully start. The liveness probe has a 90-second initial delay to accommodate this.
- **JMS Queues**: The application uses internal Payara JMS queues (CargoHandledQueue, MisdirectedCargoQueue, etc.). These are configured in `post-boot-commands.asadmin` and deployed automatically.
- **Session Affinity**: JSF (PrimeFaces) requires sticky sessions. The ALB ingress is configured with cookie-based affinity.
- **PostgreSQL Profile**: The Docker image is built with the `cloud` Maven profile, which uses PostgreSQL as the database. Ensure `POSTGRES_JDBC_URL`, `POSTGRES_USERNAME`, and `POSTGRES_PASSWORD` are set correctly.
- **Redis (ElastiCache)**: The application uses Jedis for Redis connectivity. Set `REDIS_HOST` and `REDIS_PORT` to your ElastiCache endpoint.
- **Graph Traversal URL**: The internal REST endpoint for route calculation. In Kubernetes, this should point to the service's internal DNS name or remain as `localhost` if the pathfinder runs within the same pod.
