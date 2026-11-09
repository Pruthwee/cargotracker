# Cargo Tracker Deployment Guide

This document describes how to build, push, and deploy the Eclipse Cargo Tracker application as a containerized microservice on AWS Elastic Kubernetes Service (EKS).

## Prerequisites

### System Requirements

- **Java**: Java SE 11 (JDK)
- **Maven**: 3.8+
- **Docker**: 20.10+
- **AWS CLI**: 2.x
- **kubectl**: 1.24+
- **eksctl**: 0.100+ (optional, for cluster creation)

### AWS Prerequisites

- An AWS account with IAM permissions for ECR, EKS, and EC2
- IAM user/role with the following managed policies:
  - `AmazonEC2ContainerRegistryFullAccess`
  - `AmazonEKSClusterPolicy`
  - `AmazonEKSWorkerNodePolicy`
  - `AmazonEKS_CNI_Policy`
  - `AmazonEC2ContainerRegistryPowerUser`
- AWS credentials configured via `aws configure`

## EKS Cluster Setup

### Create an EKS Cluster

```bash
# Create EKS cluster
eksctl create cluster \
  --name cargo-tracker-cluster \
  --region us-east-1 \
  --nodegroup-name cargo-tracker-nodes \
  --node-type t3.medium \
  --nodes 2 \
  --nodes-max 4 \
  --managed
```

### Configure kubectl

```bash
aws eks update-kubeconfig --region us-east-1 --name cargo-tracker-cluster
kubectl cluster-info
```

## Local Setup with Docker Compose

### Build the Docker Image

```bash
docker build -f Dockerfile -t cargo-tracker .
```

### Run Locally

```bash
# Set environment variables
export DB_HOST=localhost
export DB_PORT=5432
export DB_USER=cargotracker
export DB_PASSWORD=cargotracker
export DB_URL=jdbc:postgresql://localhost:5432/cargotracker
export GRAPH_TRAVERSAL_URL=http://localhost:8080/cargo-tracker/rest/graph-traversal/shortest-path

docker-compose up
```

The application will be available at `http://localhost:8080/cargo-tracker`.

The health check endpoint is available at `http://localhost:8080/cargo-tracker/rest/health`.

## Building and Pushing the Docker Image

### Using the Shell Script (Linux/macOS)

```bash
./scripts/build-push.sh
```

The script will:
1. Ask whether to use AWS ECR or Docker Hub
2. Prompt for registry details (AWS region, ECR repository, image tag)
3. Log in to the chosen registry
4. Create the ECR repository if it does not exist (AWS ECR only)
5. Build the Docker image
6. Push the image to the registry

### Using the Batch Script (Windows)

```cmd
scripts\build-push.bat
```

### Manual Build and Push

```bash
# For AWS ECR
aws ecr get-login-password --region us-east-1 | docker login --username AWS --password-stdin 123456789012.dkr.ecr.us-east-1.amazonaws.com
aws ecr create-repository --repository-name cargo-tracker --region us-east-1
docker build -f Dockerfile -t 123456789012.dkr.ecr.us-east-1.amazonaws.com/cargo-tracker:latest .
docker push 123456789012.dkr.ecr.us-east-1.amazonaws.com/cargo-tracker:latest
```

## Deploying to AWS EKS

### Using the Deploy Script

```bash
./scripts/deploy-image.sh
```

The script will:
1. Ask for AWS region and EKS cluster name
2. Configure kubectl for the target cluster
3. Ask for the full image URI (registry/repo:tag)
4. Ask for environment variable values (DB_URL, GRAPH_TRAVERSAL_URL)
5. Ask for credential values (DB_USER, DB_PASSWORD) without echoing
6. Create a Kubernetes Secret with the credentials
7. Apply the namespace, deployment, service, and ingress manifests
8. Wait for the deployment rollout to complete
9. Display the pod, service, and ingress status
10. Show the application URL

### Using the Batch Deploy Script (Windows)

```cmd
scripts\deploy-image.bat
```

### Manual Deployment

```bash
# Configure kubectl
aws eks update-kubeconfig --region us-east-1 --name cargo-tracker-cluster

# Create the namespace
kubectl apply -f kubernetes/namespace.yaml

# Create the secret
kubectl create secret generic cargo-tracker-secrets -n cargo-tracker \
  --from-literal=db-user=cargotracker \
  --from-literal=db-password=secretpassword \
  --dry-run=client -o yaml | kubectl apply -f -

# Create a temporary deployment with placeholders replaced
sed 's|{{IMAGE_URI}}|123456789012.dkr.ecr.us-east-1.amazonaws.com/cargo-tracker:latest|g' kubernetes/deployment.yaml | \
  sed 's|{{DB_URL}}|jdbc:postgresql://mydb.cluster-xyz.us-east-1.rds.amazonaws.com:5432/cargotracker|g' | \
  sed 's|{{GRAPH_TRAVERSAL_URL}}|http://cargo-tracker.example.com/cargo-tracker/rest/graph-traversal/shortest-path|g' | \
  kubectl apply -f -

# Apply service and ingress
kubectl apply -f kubernetes/service.yaml
kubectl apply -f kubernetes/ingress.yaml

# Wait for rollout
kubectl rollout status deployment/cargo-tracker -n cargo-tracker
```

## Kubernetes Manifests

### Namespace (`kubernetes/namespace.yaml`)

Creates a dedicated `cargo-tracker` namespace for the application.

### Deployment (`kubernetes/deployment.yaml`)

- **Replicas**: 2 (for high availability)
- **Container**: cargo-tracker with the deployed WAR
- **Port**: 8080
- **Resources**: 250m CPU / 512Mi memory (requests), 500m CPU / 1Gi memory (limits)
- **Environment Variables**:
  - `DB_URL`: Database JDBC URL (from user input)
  - `DB_USER`: Database username (from Kubernetes Secret)
  - `DB_PASSWORD`: Database password (from Kubernetes Secret)
  - `GRAPH_TRAVERSAL_URL`: URL for the graph traversal service (from user input)
  - `JAVA_OPTS`: JVM settings for container memory limits
  - `TZ`: UTC timezone
- **Liveness Probe**: HTTP GET on `/cargo-tracker/rest/health` (initial delay 60s, period 30s)
- **Readiness Probe**: HTTP GET on `/cargo-tracker/rest/health` (initial delay 30s, period 10s)

### Service (`kubernetes/service.yaml`)

- **Type**: ClusterIP
- **Port**: 80 -> 8080 (container port)
- **Selector**: app: cargo-tracker

### Ingress (`kubernetes/ingress.yaml`)

- **Annotations**: AWS Load Balancer Controller (ALB)
- **Scheme**: internet-facing
- **Host**: cargo-tracker.example.com
- **Path**: / (Prefix)

## Configuration Management

The application reads configuration from environment variables:

| Variable | Description | Default |
|---|---|---|
| `DB_URL` | Database JDBC URL | `jdbc:h2:file:./cargo-tracker-data/cargo-tracker-database` |
| `DB_USER` | Database username | (empty) |
| `DB_PASSWORD` | Database password | (empty) |
| `GRAPH_TRAVERSAL_URL` | Graph traversal REST endpoint | `http://localhost:8080/cargo-tracker/rest/graph-traversal/shortest-path` |

### Secrets Management

- Database credentials are stored in a Kubernetes Secret named `cargo-tracker-secrets`
- The secret is created during deployment using `kubectl create secret`
- Credentials are never stored in plain text in the deployment manifests

### External Services

The application requires the following external services:
- **Database**: PostgreSQL (or H2 for development)
- **Message Broker**: JMS-compatible broker for handling event messaging
- **Graph Traversal Service**: REST API for route calculation

These services are provided separately and reached through environment variables.

## Security Considerations

- **Non-root user**: The Docker image runs as a non-root user (UID 1000)
- **Secrets**: Database credentials are stored in Kubernetes Secrets, never in plain text
- **Network**: The ingress uses TLS (configure cert-manager or ACM for HTTPS)
- **Resource limits**: CPU and memory limits prevent resource exhaustion
- **Image scanning**: Scan the Docker image for vulnerabilities before pushing to ECR
- **Least privilege**: The Kubernetes service account should have minimal permissions

## Java Notes

### JVM Settings

The Docker image sets the following JVM options via `JAVA_OPTS`:

```
-Xmx512m -Xms256m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0
```

- `-Xmx512m`: Maximum heap size of 512MB
- `-Xms256m`: Initial heap size of 256MB
- `-XX:+UseContainerSupport`: Enables container-aware JVM flags (default in Java 8u191+)
- `-XX:MaxRAMPercentage=75.0`: Uses 75% of the container's memory limit for the heap

These settings are tuned for the Kubernetes resource limits (512Mi memory request, 1Gi memory limit). The container's cgroup memory limit is 1Gi, and the JVM uses 75% of that (768MB) for the heap, with additional memory for metaspace and native memory.

### Application Server

The application runs on **Payara Micro 6.2025.3**, which is an embedded Jakarta EE application server. Payara Micro provides:
- Jakarta EE 10 full platform support
- Embedded HTTP server (no separate web server needed)
- MicroProfile support
- Hot deployment capabilities

### Health Endpoint

The application exposes a health check endpoint at:

```
GET /cargo-tracker/rest/health
```

Response:
```json
{"status":"UP"}
```

This endpoint is used by Kubernetes liveness and readiness probes.

### Application Profiles

The application uses Maven profiles for different deployment targets:
- **payara** (default): Uses H2 database, designed for development
- **cloud**: Uses PostgreSQL, designed for cloud deployment
- **glassfish**: Uses H2 database with GlassFish
- **openliberty**: Uses HSQLDB with Open Liberty

For EKS deployment, use the `cloud` profile or set the database environment variables to point to your RDS instance.

## Troubleshooting

### Pod Failures

```bash
# Check pod status
kubectl get pods -n cargo-tracker

# Describe pod for events
kubectl describe pod <pod-name> -n cargo-tracker

# Check logs
kubectl logs <pod-name> -n cargo-tracker
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

# Check ingress controller logs
kubectl logs -n kube-system -l app.kubernetes.io/name=aws-load-balancer-controller
```

### Rollback

```bash
# Rollback deployment
kubectl rollout undo deployment/cargo-tracker -n cargo-tracker

# Check rollout status
kubectl rollout status deployment/cargo-tracker -n cargo-tracker

# List rollout history
kubectl rollout history deployment/cargo-tracker -n cargo-tracker
```

## Scaling and Management

### Horizontal Pod Autoscaler (HPA)

To enable automatic scaling based on CPU utilization:

```bash
kubectl autoscale deployment cargo-tracker --cpu-percent=70 --min=2 --max=10 -n cargo-tracker
```

### Manual Scaling

```bash
# Scale up
kubectl scale deployment cargo-tracker --replicas=3 -n cargo-tracker

# Scale down
kubectl scale deployment cargo-tracker --replicas=2 -n cargo-tracker
```

### Rolling Updates

```bash
# Update the image
kubectl set image deployment/cargo-tracker cargo-tracker=<new-image-uri> -n cargo-tracker

# Watch the rollout
kubectl rollout status deployment/cargo-tracker -n cargo-tracker
```
