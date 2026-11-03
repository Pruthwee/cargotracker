# Cargo Tracker Deployment Guide

This document describes how to build, deploy, and manage the Eclipse Cargo Tracker application on AWS EKS (Elastic Kubernetes Service).

## Prerequisites

### System Requirements

- **Java**: JDK 11 (for building the application)
- **Maven**: 3.8+ (or use the included `mvnw` wrapper)
- **Docker**: 20.10+ (for building and pushing container images)
- **AWS CLI**: 2.x (for ECR and EKS operations)
- **kubectl**: 1.24+ (for Kubernetes operations)
- **eksctl**: 0.149+ (for EKS cluster creation, optional)

### AWS Permissions

The following AWS permissions are required:

- `ecr:GetAuthorizationToken`, `ecr:BatchCheckLayerAvailability`, `ecr:CompleteLayerUpload`, `ecr:UploadLayerPart`, `ecr:PutImage`, `ecr:InitiateLayerUpload`, `ecr:BatchGetImage`
- `ecr:CreateRepository`, `ecr:DescribeRepositories`
- `eks:DescribeCluster`, `eks:ListClusters`
- IAM permissions for EKS cluster access (via `aws eks update-kubeconfig`)

## Local Setup with Docker Compose

To run the application locally using Docker Compose:

```bash
# Set the database password
export DB_PASSWORD=cargotracker

# Build and start the application
docker-compose up --build
```

The application will be available at `http://localhost:8080`.

### Docker Compose Configuration

The `docker-compose.yml` file defines a single service (`cargo-tracker`) with the following configuration:

- **Port**: 8080 (host) mapped to 8080 (container)
- **Environment Variables**:
  - `DB_DRIVER_CLASS`: PostgreSQL JDBC driver class
  - `DB_JDBC_URL`: JDBC connection URL for the database
  - `DB_USER`: Database username
  - `DB_PASSWORD`: Database password (from environment)
  - `GRAPH_TRAVERSAL_URL`: URL for the graph traversal REST service
- **Volumes**: Persistent data volume for the application

## Building and Pushing the Container Image

### Using build-push.sh (Linux/macOS)

```bash
chmod +x scripts/build-push.sh
./scripts/build-push.sh
```

The script will:
1. Ask you to select a target platform (AWS ECR or Docker Hub)
2. Prompt for registry details (AWS region, ECR registry URL, repository name)
3. Prompt for an image tag (defaults to `latest`)
4. Log in to the selected registry
5. Build the Docker image using a multi-stage build
6. Push the image to the registry

### Using build-push.bat (Windows)

```cmd
scripts\build-push.bat
```

The script follows the same flow as the Linux version.

### Docker Image Details

- **Base image**: `eclipse-temurin:11-jdk-alpine`
- **Application server**: Payara Micro 6.2025.3
- **Build**: Multi-stage build using `maven:3.9.4-eclipse-temurin-11`
- **JVM options**: `-Xmx512m -Xms256m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0`
- **Exposed port**: 8080

## Deploying to AWS EKS

### EKS Cluster Setup

If you need to create a new EKS cluster:

```bash
# Create EKS cluster using eksctl
eksctl create cluster \
  --name cargo-tracker \
  --region us-east-1 \
  --nodegroup-name cargo-tracker-nodes \
  --node-type t3.medium \
  --nodes 2 \
  --nodes-max 4

# Or configure kubectl for an existing cluster
aws eks update-kubeconfig --region us-east-1 --name cargo-tracker
```

### Deploying the Application

```bash
chmod +x scripts/deploy-image.sh
./scripts/deploy-image.sh
```

The script will:
1. Ask for the AWS region and EKS cluster name
2. Configure kubectl for the target EKS cluster
3. Ask for the full image URI (with registry and tag)
4. Ask for environment variable values (DB_JDBC_URL, DB_USER, GRAPH_TRAVERSAL_URL)
5. Ask for the DB_PASSWORD credential (input is hidden)
6. Create a Kubernetes Secret for the database password
7. Apply the Kubernetes manifests (namespace, deployment, service, ingress)
8. Wait for the deployment rollout to complete
9. Display the deployment status and application URL

### Kubernetes Manifests

The `kubernetes/` directory contains the following manifests:

#### namespace.yaml
Defines a dedicated namespace `cargo-tracker` for the application.

#### deployment.yaml
Defines a Kubernetes Deployment with:
- **Replicas**: 2 (for high availability)
- **Container**: `cargo-tracker` running on port 8080
- **Image**: `{{IMAGE_URI}}` (replaced at deploy time)
- **Environment Variables**:
  - `DB_DRIVER_CLASS`: PostgreSQL JDBC driver class
  - `DB_JDBC_URL`: `{{DB_JDBC_URL}}` (replaced at deploy time)
  - `DB_USER`: `{{DB_USER}}` (replaced at deploy time)
  - `DB_PASSWORD`: From Kubernetes Secret `cargo-tracker-secrets`
  - `GRAPH_TRAVERSAL_URL`: `{{GRAPH_TRAVERSAL_URL}}` (replaced at deploy time)
- **Resource Limits**: CPU 500m, Memory 1Gi
- **Resource Requests**: CPU 250m, Memory 512Mi
- **Liveness Probe**: HTTP GET to `/rest/health` on port 8080
- **Readiness Probe**: HTTP GET to `/rest/health` on port 8080

#### service.yaml
Defines a ClusterIP Service that exposes the application internally on port 80, forwarding to the container's port 8080.

#### ingress.yaml
Defines an Ingress resource with:
- **Host**: `cargo-tracker.example.com`
- **Annotations**: AWS Load Balancer Controller (`alb` ingress class, internet-facing scheme)
- **Path**: `/` (prefix) forwarding to the `cargo-tracker-service` on port 80

### Secret Management

The database password is stored in a Kubernetes Secret named `cargo-tracker-secrets` in the `cargo-tracker` namespace. The secret is created during deployment using:

```bash
kubectl create secret generic cargo-tracker-secrets -n cargo-tracker \
  --from-literal=db-password="<password>" \
  --dry-run=client -o yaml | kubectl apply -f -
```

The password is never stored in the deployment manifest or any tracked configuration file.

## Configuration Management

The application reads the following environment variables at runtime:

| Variable | Description | Default |
|---|---|---|
| `DB_DRIVER_CLASS` | JDBC driver class name | `org.postgresql.ds.PGPoolingDataSource` |
| `DB_JDBC_URL` | JDBC connection URL | `jdbc:postgresql://localhost:5432/postgres` |
| `DB_USER` | Database username | (empty) |
| `DB_PASSWORD` | Database password | (from Kubernetes Secret) |
| `GRAPH_TRAVERSAL_URL` | Graph traversal REST service URL | `http://localhost:8080/rest/graph-traversal/shortest-path` |
| `JAVA_OPTS` | JVM options | `-Xmx512m -Xms256m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0` |

These values are resolved by Payara Micro from environment variables using the `${ENV=VAR_NAME}` syntax in `web.xml`.

## Health Check Endpoint

The application provides a health check endpoint at:

```
GET /rest/health
```

Response:
```json
{"status":"UP","timestamp":"2024-01-01T12:00:00"}
```

This endpoint is used by Kubernetes for liveness and readiness probes.

## Security Considerations

- **Credentials**: Database passwords are stored in Kubernetes Secrets, never in manifests or configuration files.
- **Image Scanning**: Regularly scan container images for vulnerabilities.
- **Least Privilege**: The application runs as a non-root user in the container.
- **Network Policies**: Consider restricting network access between pods using Kubernetes Network Policies.
- **TLS**: The ALB ingress terminates TLS. Configure TLS certificates using AWS Certificate Manager.

## Troubleshooting

### Pod Failures

```bash
# Check pod status
kubectl get pods -n cargo-tracker

# Check pod logs
kubectl logs -n cargo-tracker <pod-name>

# Describe pod for events
kubectl describe pod -n cargo-tracker <pod-name>
```

### Service Issues

```bash
# Check service
kubectl get svc -n cargo-tracker

# Check endpoints
kubectl get endpoints -n cargo-tracker

# Test connectivity from within the cluster
kubectl run debug --rm -it --image=curlimages/curl -- sh
curl http://cargo-tracker-service:80/rest/health
```

### Ingress Problems

```bash
# Check ingress
kubectl get ingress -n cargo-tracker

# Check ingress events
kubectl describe ingress -n cargo-tracker

# Verify ALB was created
kubectl get ingress -n cargo-tracker -o wide
```

### Rollback

If the deployment fails or has issues:

```bash
# Rollback to previous revision
kubectl rollout undo deployment/cargo-tracker -n cargo-tracker

# Check rollout history
kubectl rollout history deployment/cargo-tracker -n cargo-tracker
```

## Scaling and Management

### Horizontal Pod Autoscaler

To enable auto-scaling based on CPU utilization:

```bash
kubectl autoscale deployment cargo-tracker -n cargo-tracker \
  --cpu-percent=70 \
  --min=2 \
  --max=10
```

### Manual Scaling

```bash
# Scale to 3 replicas
kubectl scale deployment cargo-tracker -n cargo-tracker --replicas=3
```

### Rolling Updates

When updating the image:

```bash
# Update the image
kubectl set image deployment/cargo-tracker cargo-tracker=<new-image> -n cargo-tracker

# Monitor the rollout
kubectl rollout status deployment/cargo-tracker -n cargo-tracker

# Pause a rollout
kubectl rollout pause deployment/cargo-tracker -n cargo-tracker

# Resume a rollout
kubectl rollout resume deployment/cargo-tracker -n cargo-tracker
```

## Java Notes

### JVM Settings

The container uses the following JVM options:

- `-Xmx512m`: Maximum heap size of 512 MB
- `-Xms256m`: Initial heap size of 256 MB
- `-XX:+UseContainerSupport`: Enable container-aware JVM optimizations
- `-XX:MaxRAMPercentage=75.0`: Use 75% of container memory for the heap

These settings are tuned for the container's resource limits (512Mi requests, 1Gi limits). The initial heap is set to 256MB to ensure fast startup, and the maximum heap is capped at 512MB to stay within the container's memory limit.

To adjust the JVM settings, modify the `JAVA_OPTS` environment variable in the Dockerfile or Kubernetes deployment.

### Application Server

The application runs on Payara Micro 6.2025.3, which is a lightweight, self-contained version of Payara Server. Payara Micro supports:

- Deploying WAR files via `--deploy`
- Adding libraries via `--addLibs`
- Environment variable substitution via `${ENV=VAR_NAME}` in deployment descriptors
- HTTP port configuration via `--httpPort`

### Health Endpoint

The health check endpoint is implemented as a Jakarta REST resource at `/rest/health`. It returns a JSON response with `"status":"UP"` and a timestamp. This endpoint is used by Kubernetes for liveness and readiness probes.

### Spring Profiles

This application does not use Spring Boot. It is a Jakarta EE application that uses CDI, EJB, JPA, JAX-RS, and Jakarta Faces. The application is configured via `web.xml` and `persistence.xml` deployment descriptors, with configuration values externalized via environment variables.
