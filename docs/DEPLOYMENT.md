# Cargo Tracker Deployment Guide

## Overview

This guide covers building, pushing, and deploying the Eclipse Cargo Tracker application to AWS EKS (Elastic Kubernetes Service). The application is a Jakarta EE WAR packaged application that runs on Payara Micro.

## Prerequisites

### System Requirements
- Java 11 (JDK)
- Maven 3.8+
- Docker
- AWS CLI
- kubectl
- eksctl

### AWS EKS Prerequisites
- An AWS account with permissions to create EKS clusters, ECR repositories, and IAM roles
- IAM permissions for ECR, EKS, and Secrets Manager
- An EKS cluster running Kubernetes 1.24+ with the AWS Load Balancer Controller installed

## Local Setup with Docker Compose

### Build and Run Locally

```bash
# Build the application
mvn clean package -Pcloud -DskipTests

# Start with Docker Compose
docker-compose up
```

The application will be available at `http://localhost:8080/cargo-tracker`.

### Docker Compose Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `DB_DRIVER_CLASS` | Database driver class | `org.postgresql.ds.PGPoolingDataSource` |
| `DB_JDBC_URL` | Database JDBC URL | `jdbc:postgresql://localhost:5432/postgres` |
| `DB_USER` | Database username | `cargotracker` |
| `DB_PASSWORD` | Database password | (required) |
| `GRAPH_TRAVERSAL_URL` | Graph traversal service URL | `http://localhost:8080/cargo-tracker/rest/graph-traversal/shortest-path` |
| `PROJECT_STAGE` | Jakarta Faces project stage | `Production` |

## Build and Push

### Using the Shell Script (Linux/macOS)

```bash
./scripts/build-push.sh
```

The script will:
1. Prompt for the target platform (AWS ECR or Docker Hub)
2. Ask for the registry details
3. Build the Docker image
4. Push the image to the registry

### Using the Batch Script (Windows)

```batch
scripts\build-push.bat
```

### Manual Build and Push

```bash
# For AWS ECR
aws ecr get-login-password --region us-east-1 | docker login --username AWS --password-stdin <account-id>.dkr.ecr.us-east-1.amazonaws.com
docker build -f Dockerfile -t <account-id>.dkr.ecr.us-east-1.amazonaws.com/cargo-tracker:latest .
docker push <account-id>.dkr.ecr.us-east-1.amazonaws.com/cargo-tracker:latest
```

## AWS EKS Deployment

### Cluster Setup

```bash
# Create an EKS cluster
eksctl create cluster --name cargo-tracker --region us-east-1 --nodegroup-name cargo-tracker-nodes --node-type t3.medium --nodes 2 --nodes-max 4

# Configure kubectl
aws eks update-kubeconfig --region us-east-1 --name cargo-tracker
```

### Deploy the Application

```bash
./scripts/deploy-image.sh
```

The script will:
1. Prompt for AWS region, EKS cluster name, and image URI
2. Ask for environment variable values (DB credentials, URLs, etc.)
3. Create a Kubernetes Secret for the database password
4. Deploy the namespace, deployment, service, and ingress
5. Wait for the deployment to complete
6. Display the application URL

### Kubernetes Manifests

The `kubernetes/` directory contains:

- **namespace.yaml**: Creates the `cargo-tracker` namespace
- **deployment.yaml**: Defines the Deployment with 2 replicas, health probes, and resource limits
- **service.yaml**: Exposes the application on port 80 (targeting container port 8080)
- **ingress.yaml**: Creates an ALB ingress for external access

### Rollback

If the deployment fails:

```bash
kubectl rollout undo deployment/cargo-tracker -n cargo-tracker
```

### Scaling

```bash
# Scale the deployment
kubectl scale deployment cargo-tracker --replicas=3 -n cargo-tracker

# View pods
kubectl get pods -n cargo-tracker
```

## Configuration Management

### Environment Variables

The application reads configuration from environment variables via Payara's `${ENV=VAR_NAME}` syntax in `web.xml`:

| Variable | Description |
|----------|-------------|
| `DB_DRIVER_CLASS` | JDBC driver class name |
| `DB_JDBC_URL` | Database connection URL |
| `DB_USER` | Database username |
| `DB_PASSWORD` | Database password (stored in Kubernetes Secret) |
| `GRAPH_TRAVERSAL_URL` | URL for the graph traversal service |
| `PROJECT_STAGE` | Jakarta Faces project stage (Production/Development) |

### Kubernetes Secrets

The database password is stored in a Kubernetes Secret:

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: cargo-tracker-secrets
  namespace: cargo-tracker
type: Opaque
stringData:
  db_password: <password>
```

## Security Considerations

- The database password is stored in a Kubernetes Secret, never in plain text in the deployment manifest
- The application runs as a non-root user in the container
- The container uses a minimal base image (amazoncorretto:11)
- Resource limits are set to prevent resource exhaustion
- The ingress uses an internet-facing ALB with proper annotations

## Troubleshooting

### Pod Failures

```bash
# Check pod status
kubectl get pods -n cargo-tracker

# View pod logs
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
```

### Database Connection Issues

- Verify the database URL, username, and password are correct
- Ensure the database is accessible from the EKS cluster
- Check that the Kubernetes Secret has the correct password

## Java Notes

### JVM Settings

The container sets the following JVM options:

- `-Xmx512m`: Maximum heap size of 512MB
- `-Xms256m`: Initial heap size of 256MB
- `-XX:+UseContainerSupport`: Enables container-aware JVM behavior
- `-XX:MaxRAMPercentage=75.0`: Uses up to 75% of the container's memory limit for the heap

These settings are tuned for the container's resource limits (512Mi requests, 1Gi limits). Adjust as needed based on the application's memory usage.

### Health Check Endpoint

The application exposes a health check endpoint at `/health` that returns a JSON response:

```json
{"status": "UP", "application": "cargo-tracker"}
```

The Kubernetes deployment uses this endpoint for liveness and readiness probes:
- **Liveness probe**: Checks every 30 seconds after an initial 60-second delay
- **Readiness probe**: Checks every 10 seconds after an initial 30-second delay

### Application Server

The application runs on Payara Micro, which is a lightweight, self-contained Jakarta EE application server. It deploys the WAR file directly and supports environment variable substitution in configuration files.

### Build Profile

The Docker image is built using the `cloud` Maven profile, which:
- Uses PostgreSQL as the database driver
- Includes the PostgreSQL JDBC driver in the build output
- Configures the application for cloud deployment
