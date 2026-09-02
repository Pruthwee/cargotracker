# Eclipse Cargo Tracker – Deployment Guide (AWS EKS)

## Table of Contents
1. [Overview](#overview)
2. [Prerequisites](#prerequisites)
3. [Project Analysis](#project-analysis)
4. [Local Development with Docker Compose](#local-development-with-docker-compose)
5. [Build & Push Docker Image](#build--push-docker-image)
6. [AWS EKS Deployment](#aws-eks-deployment)
7. [Kubernetes Manifest Reference](#kubernetes-manifest-reference)
8. [Configuration & Environment Variables](#configuration--environment-variables)
9. [Health Checks & Monitoring](#health-checks--monitoring)
10. [Scaling & Rolling Updates](#scaling--rolling-updates)
11. [Troubleshooting](#troubleshooting)
12. [Security Considerations](#security-considerations)

---

## Overview

**Application**: Eclipse Cargo Tracker  
**Version**: 3.1-SNAPSHOT  
**Technology**: Jakarta EE 10 (WAR), Java 11, Payara Micro  
**Build Tool**: Maven 3.9.x  
**Target Platform**: AWS EKS (Elastic Kubernetes Service)  
**Runtime Base Image**: `amazoncorretto:11`  
**Application Port**: 8080 (HTTP), 8181 (HTTPS)  
**Health Endpoint**: `GET /cargo-tracker/rest/health`

---

## Prerequisites

### Local Development
| Tool | Version | Purpose |
|------|---------|---------|
| Docker | 24.x+ | Container build & run |
| Docker Compose | 2.x+ | Local multi-service orchestration |
| Java JDK | 11+ | Local build (optional) |
| Maven | 3.9.x+ | Local build (optional) |

### AWS EKS Deployment
| Tool | Version | Purpose |
|------|---------|---------|
| AWS CLI | 2.x+ | AWS authentication & ECR |
| kubectl | 1.28+ | Kubernetes cluster management |
| eksctl | 0.170+ | EKS cluster creation (optional) |
| Docker | 24.x+ | Image build & push |

### AWS IAM Permissions Required
```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "ecr:GetAuthorizationToken",
        "ecr:BatchCheckLayerAvailability",
        "ecr:GetDownloadUrlForLayer",
        "ecr:BatchGetImage",
        "ecr:PutImage",
        "ecr:InitiateLayerUpload",
        "ecr:UploadLayerPart",
        "ecr:CompleteLayerUpload",
        "ecr:CreateRepository",
        "ecr:DescribeRepositories",
        "eks:DescribeCluster",
        "eks:ListClusters"
      ],
      "Resource": "*"
    }
  ]
}
```

---

## Project Analysis

| Property | Value |
|----------|-------|
| Framework | Jakarta EE 10 (CDI, JPA, JAX-RS, JSF/Faces, JMS, Batch) |
| Java Version | 11 |
| Build Tool | Maven |
| Packaging | WAR |
| Application Server | Payara Micro 6.2025.3 |
| Database | H2 (dev) / PostgreSQL (cloud profile) |
| Cache | Redis / Amazon ElastiCache (Jedis 5.1.0) |
| Messaging | JMS (embedded Payara messaging) |
| UI | PrimeFaces 14.0.5 (Jakarta) |
| Health Endpoint | `/cargo-tracker/rest/health` |

---

## Local Development with Docker Compose

### Quick Start

```bash
# Clone / navigate to project root
cd /path/to/cmp

# (Optional) Set environment variables
export REDIS_HOST=localhost
export REDIS_PORT=6379

# Build and start the application
docker-compose up --build

# Access the application
open http://localhost:8080/cargo-tracker
```

### Environment Variables for Local Development

Create a `.env` file in the project root:

```env
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=
DB_JDBC_URL=jdbc:h2:file:./cargo-tracker-data/cargo-tracker-database
DB_USER=
DB_PASSWORD=
GRAPH_TRAVERSAL_URL=http://localhost:8080/cargo-tracker/rest/graph-traversal/shortest-path
```

### Useful Docker Compose Commands

```bash
# Start in background
docker-compose up -d

# View logs
docker-compose logs -f cargo-tracker

# Stop services
docker-compose down

# Rebuild image
docker-compose build --no-cache

# Remove volumes
docker-compose down -v
```

---

## Build & Push Docker Image

### Linux / macOS

```bash
chmod +x scripts/build-push.sh
./scripts/build-push.sh
```

The script will prompt you to:
1. Enter an image tag (default: `latest`)
2. Select registry type (AWS ECR or Docker Hub)
3. Provide registry credentials

### Windows

```cmd
scripts\build-push.bat
```

### Manual Build (AWS ECR)

```bash
# Set variables
AWS_REGION=us-east-1
AWS_ACCOUNT_ID=123456789012
ECR_REPO=cargo-tracker
IMAGE_TAG=latest

# Authenticate
aws ecr get-login-password --region $AWS_REGION | \
  docker login --username AWS --password-stdin \
  ${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com

# Create repository (first time only)
aws ecr create-repository --repository-name $ECR_REPO --region $AWS_REGION

# Build
docker build -t ${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/${ECR_REPO}:${IMAGE_TAG} .

# Push
docker push ${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/${ECR_REPO}:${IMAGE_TAG}
```

---

## AWS EKS Deployment

### Step 1: Create or Connect to EKS Cluster

```bash
# Create a new cluster (if needed)
eksctl create cluster \
  --name cargo-tracker-cluster \
  --region us-east-1 \
  --nodegroup-name standard-workers \
  --node-type t3.medium \
  --nodes 2 \
  --nodes-min 1 \
  --nodes-max 4

# Configure kubectl for existing cluster
aws eks update-kubeconfig --region us-east-1 --name cargo-tracker-cluster

# Verify connectivity
kubectl cluster-info
kubectl get nodes
```

### Step 2: Install AWS Load Balancer Controller (for ALB Ingress)

```bash
# Add EKS chart repository
helm repo add eks https://aws.github.io/eks-charts
helm repo update

# Install AWS Load Balancer Controller
helm install aws-load-balancer-controller eks/aws-load-balancer-controller \
  -n kube-system \
  --set clusterName=cargo-tracker-cluster \
  --set serviceAccount.create=false \
  --set serviceAccount.name=aws-load-balancer-controller
```

### Step 3: Deploy Using Script

```bash
chmod +x scripts/deploy-image.sh
./scripts/deploy-image.sh
```

The script will prompt for:
- AWS region and EKS cluster name
- Full Docker image URI
- Redis/ElastiCache connection details
- Database connection details
- Graph traversal service URL

### Step 4: Manual Deployment

```bash
# Set image URI
IMAGE_URI="123456789012.dkr.ecr.us-east-1.amazonaws.com/cargo-tracker:latest"

# Update deployment manifest
sed -i "s|{{IMAGE_URI}}|${IMAGE_URI}|g" kubernetes/deployment.yaml
sed -i "s|{{REDIS_HOST}}|your-elasticache-endpoint|g" kubernetes/deployment.yaml
sed -i "s|{{REDIS_PORT}}|6379|g" kubernetes/deployment.yaml
sed -i "s|{{REDIS_PASSWORD}}|your-redis-password|g" kubernetes/deployment.yaml
sed -i "s|{{DB_JDBC_URL}}|jdbc:postgresql://your-rds-endpoint:5432/cargotracker|g" kubernetes/deployment.yaml
sed -i "s|{{DB_USER}}|cargotracker|g" kubernetes/deployment.yaml
sed -i "s|{{DB_PASSWORD}}|your-db-password|g" kubernetes/deployment.yaml
sed -i "s|{{GRAPH_TRAVERSAL_URL}}|http://cargo-tracker-service/cargo-tracker/rest/graph-traversal/shortest-path|g" kubernetes/deployment.yaml

# Apply manifests
kubectl apply -f kubernetes/namespace.yaml
kubectl apply -f kubernetes/deployment.yaml
kubectl apply -f kubernetes/service.yaml
kubectl apply -f kubernetes/ingress.yaml

# Wait for rollout
kubectl rollout status deployment/cargo-tracker -n cargo-tracker

# Verify
kubectl get pods,svc,ingress -n cargo-tracker
```

---

## Kubernetes Manifest Reference

### namespace.yaml
Creates the `cargo-tracker` namespace to isolate all application resources.

### deployment.yaml
- **Replicas**: 2 (for high availability)
- **Strategy**: RollingUpdate (zero-downtime deployments)
- **Image**: Parameterized via `{{IMAGE_URI}}` placeholder
- **Resources**: 250m CPU / 512Mi memory (requests), 500m CPU / 1Gi memory (limits)
- **Liveness Probe**: `GET /cargo-tracker/rest/health` (initial delay: 90s)
- **Readiness Probe**: `GET /cargo-tracker/rest/health` (initial delay: 60s)
- **Security**: Runs as non-root user (UID 1000)

### service.yaml
- **Type**: ClusterIP (internal cluster access)
- **Port**: 80 → 8080 (HTTP)

### ingress.yaml
- **Controller**: AWS ALB (Application Load Balancer)
- **Scheme**: internet-facing
- **Stickiness**: Enabled (lb_cookie, 48h duration) — required for JSF session state
- **Health Check**: `/cargo-tracker/rest/health`

---

## Configuration & Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `REDIS_HOST` | `localhost` | Amazon ElastiCache Redis endpoint |
| `REDIS_PORT` | `6379` | Redis port |
| `REDIS_PASSWORD` | _(empty)_ | Redis auth token |
| `DB_JDBC_URL` | H2 file URL | JDBC URL for the database |
| `DB_USER` | _(empty)_ | Database username |
| `DB_PASSWORD` | _(empty)_ | Database password |
| `GRAPH_TRAVERSAL_URL` | localhost URL | URL of the graph traversal REST service |
| `JAVA_OPTS` | JVM flags | JVM memory and container settings |
| `TZ` | `UTC` | Container timezone |

### Using AWS Secrets Manager (Recommended for Production)

```bash
# Store secrets
aws secretsmanager create-secret \
  --name cargo-tracker/db-password \
  --secret-string "your-db-password"

# Use External Secrets Operator to sync to Kubernetes Secrets
# Then reference in deployment.yaml:
# env:
#   - name: DB_PASSWORD
#     valueFrom:
#       secretKeyRef:
#         name: cargo-tracker-secrets
#         key: db-password
```

---

## Health Checks & Monitoring

### Health Endpoint

```bash
# Test health endpoint
curl http://localhost:8080/cargo-tracker/rest/health

# Expected response
{
  "status": "UP",
  "application": "cargo-tracker",
  "timestamp": "2024-01-01T00:00:00Z"
}
```

### Kubernetes Probe Configuration

The deployment uses HTTP probes against `/cargo-tracker/rest/health`:

- **Liveness Probe**: Restarts the pod if the application becomes unresponsive
  - Initial delay: 90s (JVM + Payara startup time)
  - Period: 30s
  - Failure threshold: 3

- **Readiness Probe**: Removes pod from load balancer if not ready
  - Initial delay: 60s
  - Period: 15s
  - Failure threshold: 3

### CloudWatch Integration

```bash
# Install CloudWatch agent for EKS
kubectl apply -f https://raw.githubusercontent.com/aws-samples/amazon-cloudwatch-container-insights/latest/k8s-deployment-manifest-templates/deployment-mode/daemonset/container-insights-monitoring/cloudwatch-namespace.yaml
```

---

## Scaling & Rolling Updates

### Manual Scaling

```bash
# Scale to 4 replicas
kubectl scale deployment cargo-tracker -n cargo-tracker --replicas=4

# Check scaling status
kubectl get pods -n cargo-tracker -w
```

### Horizontal Pod Autoscaler (HPA)

```bash
# Create HPA (scale between 2-10 pods based on CPU)
kubectl autoscale deployment cargo-tracker \
  -n cargo-tracker \
  --cpu-percent=70 \
  --min=2 \
  --max=10

# Check HPA status
kubectl get hpa -n cargo-tracker
```

### Rolling Update

```bash
# Update image
kubectl set image deployment/cargo-tracker \
  cargo-tracker=123456789012.dkr.ecr.us-east-1.amazonaws.com/cargo-tracker:v2.0 \
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
kubectl rollout undo deployment/cargo-tracker -n cargo-tracker --to-revision=2
```

---

## Troubleshooting

### Pod Not Starting

```bash
# Check pod status
kubectl get pods -n cargo-tracker

# Describe pod for events
kubectl describe pod <pod-name> -n cargo-tracker

# View pod logs
kubectl logs <pod-name> -n cargo-tracker

# View previous container logs (if crashed)
kubectl logs <pod-name> -n cargo-tracker --previous
```

### Common Issues

#### 1. ImagePullBackOff
```bash
# Check ECR authentication
aws ecr get-login-password --region us-east-1 | \
  docker login --username AWS --password-stdin \
  123456789012.dkr.ecr.us-east-1.amazonaws.com

# Verify image exists
aws ecr describe-images --repository-name cargo-tracker --region us-east-1
```

#### 2. CrashLoopBackOff (JVM / Payara startup failure)
```bash
# Check logs for startup errors
kubectl logs <pod-name> -n cargo-tracker

# Common causes:
# - Insufficient memory (increase limits in deployment.yaml)
# - Database connection failure (check DB_JDBC_URL, DB_USER, DB_PASSWORD)
# - Redis connection failure (check REDIS_HOST, REDIS_PORT)
```

#### 3. Readiness Probe Failing
```bash
# Check if health endpoint is accessible
kubectl exec -it <pod-name> -n cargo-tracker -- \
  java -cp /opt/payara/payara-micro.jar fish.payara.micro.PayaraMicro --help

# Increase initialDelaySeconds in deployment.yaml if Payara needs more startup time
```

#### 4. Ingress Not Getting External IP
```bash
# Check ALB controller logs
kubectl logs -n kube-system -l app.kubernetes.io/name=aws-load-balancer-controller

# Verify ingress annotations
kubectl describe ingress cargo-tracker-ingress -n cargo-tracker
```

#### 5. Session Affinity Issues (JSF)
The ingress is configured with ALB sticky sessions (lb_cookie). If users experience session loss:
```bash
# Verify stickiness annotations are applied
kubectl get ingress cargo-tracker-ingress -n cargo-tracker -o yaml | grep stickiness
```

### Resource Issues

```bash
# Check resource usage
kubectl top pods -n cargo-tracker
kubectl top nodes

# Check resource quotas
kubectl describe namespace cargo-tracker
```

---

## Security Considerations

1. **Non-root Container**: The application runs as UID 1000 (`cargotracker` user)
2. **Secrets Management**: Use AWS Secrets Manager + External Secrets Operator for sensitive values
3. **Network Policies**: Consider adding Kubernetes NetworkPolicy to restrict pod-to-pod communication
4. **Image Scanning**: Enable ECR image scanning for vulnerability detection
5. **HTTPS**: Configure ACM certificate ARN in ingress annotations for TLS termination:
   ```yaml
   alb.ingress.kubernetes.io/certificate-arn: arn:aws:acm:us-east-1:123456789012:certificate/xxx
   ```
6. **RBAC**: Apply least-privilege IAM roles for EKS node groups
7. **Pod Security**: Consider adding `securityContext.readOnlyRootFilesystem: true` after testing

### TLS Configuration

```yaml
# Add to ingress.yaml annotations:
alb.ingress.kubernetes.io/certificate-arn: "arn:aws:acm:REGION:ACCOUNT:certificate/CERT-ID"
alb.ingress.kubernetes.io/ssl-policy: "ELBSecurityPolicy-TLS13-1-2-2021-06"
```

---

## Technology-Specific Notes

### Jakarta EE / Payara Micro
- The application is packaged as a WAR and deployed to Payara Micro at startup
- Payara Micro is embedded in the Docker image as a fat JAR
- JMS messaging uses Payara's embedded messaging engine (no external broker needed)
- Batch processing uses Jakarta Batch 2.1 (embedded in Payara)

### JVM Tuning for Containers
The following JVM flags are set via `JAVA_OPTS`:
```
-Xms256m                    # Initial heap size
-Xmx512m                    # Maximum heap size
-XX:+UseContainerSupport    # Enable container-aware JVM
-XX:MaxRAMPercentage=75.0   # Use 75% of container memory for heap
-XX:+UnlockExperimentalVMOptions
```

Adjust `JAVA_OPTS` in `kubernetes/deployment.yaml` based on observed memory usage.

### Redis / ElastiCache
- The application uses Redis for distributed caching (replacing in-process caches)
- Configure `REDIS_HOST` to point to your Amazon ElastiCache Redis endpoint
- For ElastiCache with TLS, update the Jedis client configuration in `RedisConfig.java`

### Database
- **Development**: H2 in-file mode (no external database needed)
- **Production (cloud profile)**: PostgreSQL via Amazon RDS
- Build with cloud profile for PostgreSQL: `mvn clean package -Pcloud -DpostgreSqlJdbcUrl=... -DpostgreSqlUsername=... -DpostgreSqlPassword=...`
