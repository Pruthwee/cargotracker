# Cargo Tracker - Deployment Guide

## Overview

This document covers how to build, deploy, and manage the Eclipse Cargo Tracker application on AWS EKS (Elastic Kubernetes Service). The application is a Jakarta EE 10 WAR packaged with Maven and runs on Payara Micro inside a container based on `amazoncorretto:11`.

## Prerequisites

### System Requirements

- Java 11 (JDK)
- Maven 3.8+
- Docker 20.10+
- AWS CLI 2.x
- kubectl 1.24+
- eksctl (for EKS cluster creation)

### AWS Permissions

The following AWS permissions are required:

- `ecr:GetAuthorizationToken`, `ecr:BatchCheckLayerAvailability`, `ecr:GetDownloadUrlForLayer`, `ecr:BatchGetImage`, `ecr:PutImage`, `ecr:InitiateLayerUpload`, `ecr:UploadLayerPart`, `ecr:CompleteLayerUpload` (for ECR push)
- `ecr:CreateRepository`, `ecr:DescribeRepositories` (for repository creation)
- `eks:DescribeCluster` (for kubectl configuration)
- `sts:GetCallerIdentity` (for AWS account identification)

## EKS Cluster Setup

### 1. Create an EKS Cluster

```bash
eksctl create cluster \
  --name cargo-tracker \
  --region us-east-1 \
  --nodegroup-name cargo-tracker-nodes \
  --node-type t3.medium \
  --nodes 2 \
  --nodes-max 4 \
  --managed
```

### 2. Configure kubectl

```bash
aws eks update-kubeconfig --region us-east-1 --name cargo-tracker
```

### 3. Install the AWS Load Balancer Controller

Follow the official AWS guide to install the ALB Ingress Controller:

```bash
eksctl utils associate-iam-oidc-provider \
  --region us-east-1 \
  --cluster cargo-tracker \
  --approve
```

Then install the AWS Load Balancer Controller using Helm or the official manifest.

### 4. Create an ECR Repository

```bash
aws ecr create-repository --repository-name cargo-tracker --region us-east-1
```

## Local Setup with Docker Compose

### 1. Build and Run Locally

```bash
# Set environment variables
export DB_URL="jdbc:postgresql://postgres:5432/postgres"
export DB_USER="cargotracker"
export DB_PASSWORD="cargotracker"
export GRAPH_TRAVERSAL_URL="http://localhost:8080/rest/graph-traversal/shortest-path"

# Build and start with Docker Compose
docker-compose up --build
```

The application will be available at `http://localhost:8080`.

### 2. Health Check

The application exposes a health check endpoint at:

```
GET http://localhost:8080/rest/health
```

Response:
```json
{"status":"UP","timestamp":"2024-01-01T12:00:00"}
```

## Building and Pushing the Container Image

### Using build-push.sh (Linux/macOS)

```bash
./scripts/build-push.sh
```

The script will:
1. Ask for the target registry (AWS ECR or Docker Hub)
2. Ask for the image tag
3. Build the Docker image
4. Log in to the registry
5. Create the ECR repository if it does not exist
6. Push the image

### Using build-push.bat (Windows)

```cmd
scripts\build-push.bat
```

### Manual Build

```bash
docker build -f Dockerfile -t cargo-tracker:latest .
```

## Deploying to AWS EKS

### Using deploy-image.sh (Linux/macOS)

```bash
./scripts/deploy-image.sh
```

The script will:
1. Ask for the AWS region and EKS cluster name
2. Ask for the full image URI
3. Ask for environment variable values (GRAPH_TRAVERSAL_URL)
4. Ask for credentials (DB_URL, DB_USER, DB_PASSWORD)
5. Configure kubectl for the EKS cluster
6. Apply the namespace
7. Create a Kubernetes Secret with credentials
8. Deploy the application (Deployment, Service, Ingress)
9. Wait for rollout to complete
10. Display the deployed resources and application URL

### Using deploy-image.bat (Windows)

```cmd
scripts\deploy-image.bat
```

### Manual Deployment

```bash
# Configure kubectl
aws eks update-kubeconfig --region us-east-1 --name cargo-tracker

# Apply namespace
kubectl apply -f kubernetes/namespace.yaml

# Create secret
kubectl create secret generic cargo-tracker-secrets -n cargo-tracker \
  --from-literal=db-url="jdbc:postgresql://your-db:5432/postgres" \
  --from-literal=db-user="cargotracker" \
  --from-literal=db-password="your-password" \
  --dry-run=client -o yaml | kubectl apply -f -

# Replace placeholders and deploy
sed -i 's|{{IMAGE_URI}}|123456789.dkr.ecr.us-east-1.amazonaws.com/cargo-tracker:latest|g' kubernetes/deployment.yaml
sed -i 's|{{NAMESPACE}}|cargo-tracker|g' kubernetes/deployment.yaml
sed -i 's|{{GRAPH_TRAVERSAL_URL}}|http://cargo-tracker:8080/rest/graph-traversal/shortest-path|g' kubernetes/deployment.yaml

kubectl apply -f kubernetes/deployment.yaml
kubectl apply -f kubernetes/service.yaml
kubectl apply -f kubernetes/ingress.yaml

# Wait for rollout
kubectl rollout status deployment/cargo-tracker -n cargo-tracker
```

## Kubernetes Manifests

### Namespace (`kubernetes/namespace.yaml`)

Creates a dedicated namespace `cargo-tracker` for the application.

### Deployment (`kubernetes/deployment.yaml`)

- **Replicas**: 2 (for high availability)
- **Container port**: 8080
- **Resources**:
  - Requests: 250m CPU, 512Mi memory
  - Limits: 500m CPU, 1Gi memory
- **Environment variables**:
  - `DB_URL`, `DB_USER`, `DB_PASSWORD` (from Kubernetes Secret)
  - `GRAPH_TRAVERSAL_URL` (configurable)
  - `JAVA_OPTS`, `TZ`, `JAVA_TOOL_OPTIONS` (JVM tuning)
- **Liveness probe**: HTTP GET on `/rest/health` (port 8080)
- **Readiness probe**: HTTP GET on `/rest/health` (port 8080)
- **Image pull secret**: `ecr-cred` (for ECR authentication)

### Service (`kubernetes/service.yaml`)

- **Type**: ClusterIP
- **Port**: 80 (targetPort: 8080)
- **Selector**: `app: cargo-tracker`

### Ingress (`kubernetes/ingress.yaml`)

- **Annotations**: AWS Load Balancer Controller (ALB, internet-facing)
- **Host**: `cargo-tracker.example.com`
- **Path**: `/` (Prefix)
- **Backend**: `cargo-tracker-service` on port 80

## Configuration Management

### Environment Variables

| Variable | Description | Required |
|---|---|---|
| `DB_URL` | PostgreSQL JDBC URL | Yes |
| `DB_USER` | Database username | Yes |
| `DB_PASSWORD` | Database password | Yes |
| `GRAPH_TRAVERSAL_URL` | URL for the graph traversal REST service | No (defaults to localhost) |
| `JAVA_OPTS` | JVM options | No |
| `TZ` | Timezone | No (defaults to UTC) |
| `JAVA_TOOL_OPTIONS` | JVM tool options | No |

### Secrets

Database credentials are stored in a Kubernetes Secret named `cargo-tracker-secrets` in the `cargo-tracker` namespace. The secret contains:

- `db-url`: The JDBC URL for the database
- `db-user`: The database username
- `db-password`: The database password

### Configuration Externalization

The application uses Payara Micro's environment variable substitution. The following environment variables are resolved at runtime:

- `${ENV=DB_URL}` → `DB_URL` environment variable
- `${ENV=DB_USER}` → `DB_USER` environment variable
- `${ENV=DB_PASSWORD}` → `DB_PASSWORD` environment variable
- `${ENV=GRAPH_TRAVERSAL_URL}` → `GRAPH_TRAVERSAL_URL` environment variable

## Security Considerations

1. **Credentials**: All database credentials are stored in Kubernetes Secrets, never in the container image or deployment manifests.
2. **Non-root user**: The container runs as a non-root user (`cargotracker`).
3. **Minimal base image**: The runtime uses `amazoncorretto:11`, a minimal Corretto image.
4. **No health check tools**: The container does not install `curl` or `wget` for health checks. Health checks use the application's own REST endpoint.
5. **ECR authentication**: The deployment uses an `imagePullSecret` for ECR authentication.
6. **HTTPS**: The ALB ingress terminates TLS. Configure TLS certificates via the ALB Ingress annotations.

## Java Notes

### JVM Settings

The container sets the following JVM options via `JAVA_OPTS`:

- `-Xmx512m`: Maximum heap size of 512 MB
- `-Xms256m`: Initial heap size of 256 MB
- `-XX:+UseContainerSupport`: Enables container support for memory limits
- `-XX:MaxRAMPercentage=75.0`: Uses 75% of the container's memory limit for the heap

These settings are tuned for the Kubernetes resource limits:
- Requests: 512Mi memory
- Limits: 1Gi memory

The `MaxRAMPercentage=75.0` ensures the heap stays within the container's memory limit, leaving room for non-heap memory (metaspace, thread stacks, etc.).

### Spring Profiles

This application does not use Spring Boot, so there are no Spring profiles. The application uses Jakarta EE profiles via the Maven `cloud` profile, which configures PostgreSQL as the database.

### Health Endpoint

The application exposes a health check endpoint at `/rest/health` (HTTP GET). This endpoint returns a JSON response with the application status:

```json
{"status":"UP","timestamp":"2024-01-01T12:00:00"}
```

The Kubernetes liveness and readiness probes use this endpoint to determine if the application is healthy and ready to serve traffic.

### Application Server

The application runs on Payara Micro, embedded in the container. Payara Micro is a lightweight, self-contained version of Payara Server that can run WAR files directly. It is started with:

```bash
java -jar payara-micro.jar --deploy cargo-tracker.war --port 8080 --contextRoot /
```

## Troubleshooting

### Pod Failures

```bash
# Check pod status
kubectl get pods -n cargo-tracker

# Check pod logs
kubectl logs <pod-name> -n cargo-tracker

# Describe pod for events
kubectl describe pod <pod-name> -n cargo-tracker
```

### Service Issues

```bash
# Check service
kubectl get svc -n cargo-tracker

# Describe service
kubectl describe svc cargo-tracker-service -n cargo-tracker
```

### Ingress Problems

```bash
# Check ingress
kubectl get ingress -n cargo-tracker

# Describe ingress
kubectl describe ingress cargo-tracker-ingress -n cargo-tracker

# Check ALB logs
kubectl logs -n kube-system <alb-controller-pod>
```

### Database Connection Issues

Ensure the following:
1. The Kubernetes Secret `cargo-tracker-secrets` is created with the correct DB credentials
2. The database is accessible from the EKS cluster (security groups, VPC routing)
3. The `DB_URL` environment variable is set to the correct JDBC URL

### Rollback

```bash
# Rollback to previous deployment
kubectl rollout undo deployment/cargo-tracker -n cargo-tracker

# Check rollout history
kubectl rollout history deployment/cargo-tracker -n cargo-tracker
```

## Scaling and Management

### Horizontal Pod Autoscaler

To create an HPA for the application:

```bash
kubectl autoscale deployment cargo-tracker \
  --cpu-percent=70 \
  --min=2 \
  --max=10 \
  -n cargo-tracker
```

### Rolling Updates

To update the application with a new image:

```bash
# Update the image
kubectl set image deployment/cargo-tracker \
  cargo-tracker=<new-image-uri> \
  -n cargo-tracker

# Monitor rollout
kubectl rollout status deployment/cargo-tracker -n cargo-tracker
```

### Resource Scaling

To scale the number of replicas:

```bash
kubectl scale deployment cargo-tracker \
  --replicas=3 \
  -n cargo-tracker
```
