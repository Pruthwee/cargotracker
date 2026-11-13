# Cargo Tracker - Deployment Guide

## Overview

Cargo Tracker is a Jakarta EE application that demonstrates Domain-Driven Design (DDD) best practices. It runs on Payara Micro within a containerized environment on AWS Elastic Kubernetes Service (EKS).

## Prerequisites

### System Requirements

- **Java**: JDK 17 (for building), JDK 11 (runtime in container)
- **Maven**: 3.9+
- **Docker**: 24+
- **AWS CLI**: 2.x
- **kubectl**: 1.24+
- **eksctl**: 0.149+ (for EKS cluster creation)

### AWS Permissions

The deploying user/role must have permissions for:
- Amazon ECR (create-repository, batch-get, put-image)
- Amazon EKS (eks:DescribeCluster, eks:ListClusters)
- Kubernetes (kubectl apply, get, describe, rollout)

## Local Setup with Docker Compose

### Build and Run Locally

```bash
# Build the application
./mvnw clean package -Pcloud -DskipTests

# Start with Docker Compose (requires an external PostgreSQL database)
# Set environment variables for the database
export DB_JDBC_URL="jdbc:postgresql://localhost:5432/postgres"
export DB_USER="cargotracker"
export DB_PASSWORD="cargotracker"

docker-compose up --build
```

The application will be available at `http://localhost:8080/cargo-tracker`.

### Health Check

The application exposes a health check endpoint at:

```
GET /rest/health
```

Response:
```json
{"status":"UP"}
```

## Building and Pushing to AWS ECR

### Using Shell Script (Linux/macOS)

```bash
# Make the script executable
chmod +x scripts/build-push.sh

# Run the build and push script
./scripts/build-push.sh
```

The script will prompt for:
1. AWS region (e.g., `us-east-1`)
2. ECR repository name (default: `cargo-tracker`)
3. Image tag (default: `latest`)
4. AWS account ID

### Using Batch Script (Windows)

```cmd
scripts\build-push.bat
```

### Manual Build and Push

```bash
# Login to AWS ECR
aws ecr get-login-password --region us-east-1 | docker login --username AWS --password-stdin <account-id>.dkr.ecr.us-east-1.amazonaws.com

# Build the image
docker build -f Dockerfile -t <account-id>.dkr.ecr.us-east-1.amazonaws.com/cargo-tracker:latest .

# Push the image
docker push <account-id>.dkr.ecr.us-east-1.amazonaws.com/cargo-tracker:latest
```

## AWS EKS Deployment

### EKS Cluster Setup

If you don't have an EKS cluster yet:

```bash
# Create EKS cluster
eksctl create cluster \
  --name cargo-tracker-cluster \
  --region us-east-1 \
  --nodegroup-name cargo-tracker-nodes \
  --node-type t3.medium \
  --nodes 2 \
  --nodes-max 4

# Configure kubectl
aws eks update-kubeconfig --region us-east-1 --name cargo-tracker-cluster
```

### Deploy the Application

1. Ensure you have built and pushed the Docker image to ECR.

2. Run the deployment script:

```bash
chmod +x scripts/deploy-image.sh
./scripts/deploy-image.sh
```

The script will prompt for:
1. AWS region
2. EKS cluster name
3. Full image URI (from ECR)
4. Environment variables (DB_JDBC_URL, DB_USER, DB_PASSWORD, GRAPH_TRAVERSAL_URL)

3. Or deploy manually:

```bash
# Create namespace
kubectl apply -f kubernetes/namespace.yaml

# Create secrets
kubectl create secret generic cargo-tracker-secrets -n cargo-tracker \
  --from-literal=db-user="cargotracker" \
  --from-literal=db-password="your-password"

# Apply deployment (after replacing placeholders)
kubectl apply -f kubernetes/deployment.yaml

# Apply service
kubectl apply -f kubernetes/service.yaml

# Apply ingress
kubectl apply -f kubernetes/ingress.yaml

# Wait for rollout
kubectl rollout status deployment/cargo-tracker -n cargo-tracker
```

### Kubernetes Manifests

#### Namespace (`kubernetes/namespace.yaml`)

Creates the `cargo-tracker` namespace for all application resources.

#### Deployment (`kubernetes/deployment.yaml`)

- **Replicas**: 2 (for high availability)
- **Container**: cargo-tracker
- **Port**: 8080
- **Resources**: 250m CPU / 512Mi memory (requests), 500m CPU / 1Gi memory (limits)
- **Liveness Probe**: HTTP GET `/rest/health` on port 8080
- **Readiness Probe**: HTTP GET `/rest/health` on port 8080
- **Secrets**: Database credentials from Kubernetes Secret `cargo-tracker-secrets`

#### Service (`kubernetes/service.yaml`)

- **Type**: ClusterIP
- **Port**: 80 → 8080 (container port)
- **Selector**: app: cargo-tracker

#### Ingress (`kubernetes/ingress.yaml`)

- **Ingress Class**: ALB (AWS Load Balancer Controller)
- **Scheme**: internet-facing
- **Host**: cargo-tracker.example.com
- **Path**: / (prefix)

### Rollback

If the deployment fails:

```bash
# Rollback to previous revision
kubectl rollout undo deployment/cargo-tracker -n cargo-tracker

# Check rollout status
kubectl rollout status deployment/cargo-tracker -n cargo-tracker

# View pod logs
kubectl logs -f deployment/cargo-tracker -n cargo-tracker
```

## Configuration Management

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `DB_DRIVER_CLASS` | JDBC driver class | `org.postgresql.ds.PGPoolingDataSource` |
| `DB_JDBC_URL` | Database JDBC URL | (required) |
| `DB_USER` | Database username | (from secret) |
| `DB_PASSWORD` | Database password | (from secret) |
| `GRAPH_TRAVERSAL_URL` | Graph traversal service URL | `http://localhost:8080/rest/graph-traversal/shortest-path` |
| `JAVA_OPTS` | JVM options | `-Xmx512m -Xms256m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0` |

### Secrets

Database credentials are stored in a Kubernetes Secret named `cargo-tracker-secrets`:

```bash
kubectl create secret generic cargo-tracker-secrets -n cargo-tracker \
  --from-literal=db-user="cargotracker" \
  --from-literal=db-password="your-password"
```

## Security Considerations

- **Non-root user**: The container runs as `appuser` (non-root)
- **Secrets management**: Database credentials are stored in Kubernetes Secrets, never in plain text
- **Image scanning**: Scan the container image for vulnerabilities before pushing to ECR
- **Network policies**: Consider adding Kubernetes Network Policies to restrict ingress/egress traffic
- **TLS**: Terminate TLS at the ALB ingress level

## Scaling and Management

### Horizontal Pod Autoscaler (HPA)

To enable auto-scaling based on CPU utilization:

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: cargo-tracker-hpa
  namespace: cargo-tracker
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: cargo-tracker
  minReplicas: 2
  maxReplicas: 10
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 65
```

Apply with:
```bash
kubectl apply -f kubernetes/hpa.yaml
```

### Rolling Updates

Update the image:

```bash
# Update the image in the deployment
kubectl set image deployment/cargo-tracker cargo-tracker=<new-image-uri> -n cargo-tracker

# Monitor the rollout
kubectl rollout status deployment/cargo-tracker -n cargo-tracker
```

### Troubleshooting

#### Pod Failures

```bash
# Check pod status
kubectl get pods -n cargo-tracker

# Describe pod for events
kubectl describe pod <pod-name> -n cargo-tracker

# Check logs
kubectl logs <pod-name> -n cargo-tracker
```

#### Service Issues

```bash
# Check service
kubectl get svc -n cargo-tracker

# Test service connectivity
kubectl exec -it <pod-name> -n cargo-tracker -- curl http://cargo-tracker-service:80/rest/health
```

#### Ingress Problems

```bash
# Check ingress status
kubectl get ingress -n cargo-tracker

# Check ingress controller logs
kubectl logs -n kube-system -l app.kubernetes.io/name=aws-load-balancer-controller
```

## Java Notes

### JVM Settings

The container uses the following JVM settings (via `JAVA_OPTS`):

- `-Xmx512m`: Maximum heap size of 512MB
- `-Xms256m`: Initial heap size of 256MB
- `-XX:+UseContainerSupport`: Enable container-aware JVM flags
- `-XX:MaxRAMPercentage=75.0`: Use up to 75% of container memory for heap

These settings are tuned for the deployment's resource limits (512Mi request, 1Gi limit). Adjust as needed based on application profiling.

### Application Server

The application runs on **Payara Micro 6.2025.3** within the `amazoncorretto:11` runtime container. Payara Micro provides:

- Embedded Jakarta EE 10 runtime
- Automatic WAR deployment
- Built-in HTTP server on port 8080
- Support for environment variable substitution in configuration

### Health Endpoint

The application exposes a health check endpoint at `/rest/health` (JAX-RS resource). This endpoint is used by:

- Kubernetes liveness and readiness probes
- AWS ALB health checks (configured via `alb.ingress.kubernetes.io/healthcheck-path: /rest/health`)
- Docker Compose health checks (if configured)

The endpoint returns `{"status":"UP"}` with HTTP 200 when the application is running.

### Application Context Path

The application is deployed with context path `/cargo-tracker` (configured in `glassfish-web.xml`). All REST endpoints are accessible under `/cargo-tracker/rest/`.

### Database Configuration

The application uses a Jakarta EE DataSource configured in `web.xml` with environment variable substitution:

- Driver: PostgreSQL JDBC (`org.postgresql.ds.PGPoolingDataSource`)
- Connection pool: Max 32, Min 2 connections
- Credentials: Provided via Kubernetes Secrets
- URL: Provided via environment variable `DB_JDBC_URL`
