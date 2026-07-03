# Eclipse Cargo Tracker - Deployment Guide

## Overview

This guide covers the complete deployment process for the **Eclipse Cargo Tracker** application — a Jakarta EE 10 web application demonstrating Domain-Driven Design (DDD) patterns. The application runs on **Payara Server** and is packaged as a WAR file.

- **Application**: Eclipse Cargo Tracker v3.1-SNAPSHOT
- **Framework**: Jakarta EE 10 (Payara Server)
- **Java Version**: Java 11
- **Build Tool**: Maven
- **Package Type**: WAR
- **Default Port**: 8080 (HTTP), 8081 (HTTPS)
- **Context Root**: `/cargo-tracker`
- **Target Platform**: AWS EKS (Elastic Kubernetes Service)

---

## Prerequisites

### Local Development
- Java 11 (Amazon Corretto 11 or Eclipse Temurin 11)
- Maven 3.9+
- Docker Desktop 24+
- Docker Compose v2+

### AWS EKS Deployment
- AWS CLI v2 configured with appropriate IAM permissions
- `kubectl` v1.28+
- `eksctl` (optional, for cluster creation)
- An existing AWS EKS cluster
- AWS Load Balancer Controller installed on the EKS cluster
- ECR repository (auto-created by build-push.sh)

### Required IAM Permissions
```
ecr:GetAuthorizationToken
ecr:BatchCheckLayerAvailability
ecr:GetDownloadUrlForLayer
ecr:BatchGetImage
ecr:CreateRepository
ecr:DescribeRepositories
ecr:PutImage
eks:DescribeCluster
eks:ListClusters
```

---

## Project Structure

```
cargotracker-container/
├── Dockerfile                    # Multi-stage Docker build
├── docker-compose.yml            # Local development compose file
├── .dockerignore                 # Docker build exclusions
├── pom.xml                       # Maven build configuration
├── post-boot-commands.asadmin    # Payara post-boot commands
├── kubernetes/
│   ├── namespace.yaml            # Kubernetes namespace
│   ├── deployment.yaml           # Application deployment
│   ├── service.yaml              # ClusterIP service
│   └── ingress.yaml              # AWS ALB ingress
├── scripts/
│   ├── build-push.sh             # Linux/macOS build & push
│   ├── build-push.bat            # Windows build & push
│   ├── deploy-image.sh           # Linux/macOS EKS deploy
│   └── deploy-image.bat          # Windows EKS deploy
└── docs/
    └── DEPLOYMENT.md             # This file
```

---

## Local Development Setup

### 1. Build the Application Locally

```bash
# Build with default Payara profile (H2 database)
mvn clean package -Ppayara -DskipTests

# Build with cloud profile (PostgreSQL)
mvn clean package -Pcloud -DskipTests \
  -DpostgreSqlJdbcUrl="jdbc:postgresql://localhost:5432/cargotracker" \
  -DpostgreSqlUsername="postgres" \
  -DpostgreSqlPassword="postgres"
```

### 2. Run with Docker Compose

```bash
# Start the application
docker-compose up -d

# View logs
docker-compose logs -f cargo-tracker

# Stop the application
docker-compose down
```

The application will be available at: http://localhost:8080/cargo-tracker

### 3. Environment Variables for Docker Compose

Create a `.env` file in the project root:

```env
DB_JDBC_URL=jdbc:h2:file:./cargo-tracker-data/cargo-tracker-database
DB_DRIVER_CLASS=org.h2.jdbcx.JdbcDataSource
DB_USER=
DB_PASSWORD=
GRAPH_TRAVERSAL_URL=http://localhost:8080/cargo-tracker/rest/graph-traversal/shortest-path
```

For PostgreSQL:
```env
DB_JDBC_URL=jdbc:postgresql://your-db-host:5432/cargotracker
DB_DRIVER_CLASS=org.postgresql.ds.PGPoolingDataSource
DB_USER=postgres
DB_PASSWORD=your-password
GRAPH_TRAVERSAL_URL=http://localhost:8080/cargo-tracker/rest/graph-traversal/shortest-path
```

---

## Docker Build & Push

### Linux/macOS

```bash
# Make the script executable
chmod +x scripts/build-push.sh

# Run from project root
./scripts/build-push.sh
```

The script will prompt you to:
1. Enter an image tag (default: `latest`)
2. Select registry type (AWS ECR or Docker Hub)
3. Enter registry credentials and details

### Windows

```cmd
scripts\build-push.bat
```

### Manual Docker Build

```bash
# Build image
docker build -t cargo-tracker:latest .

# Tag for ECR
docker tag cargo-tracker:latest 123456789.dkr.ecr.us-east-1.amazonaws.com/cargo-tracker:latest

# Push to ECR
aws ecr get-login-password --region us-east-1 | docker login --username AWS --password-stdin 123456789.dkr.ecr.us-east-1.amazonaws.com
docker push 123456789.dkr.ecr.us-east-1.amazonaws.com/cargo-tracker:latest
```

---

## AWS EKS Deployment

### Prerequisites Setup

#### 1. Configure AWS CLI
```bash
aws configure
# Enter: AWS Access Key ID, Secret Access Key, Region, Output format
```

#### 2. Configure kubectl for EKS
```bash
aws eks update-kubeconfig --region us-east-1 --name your-cluster-name
kubectl cluster-info
```

#### 3. Install AWS Load Balancer Controller (if not installed)
```bash
# Add the EKS chart repo
helm repo add eks https://aws.github.io/eks-charts
helm repo update

# Install the controller
helm install aws-load-balancer-controller eks/aws-load-balancer-controller \
  -n kube-system \
  --set clusterName=your-cluster-name \
  --set serviceAccount.create=false \
  --set serviceAccount.name=aws-load-balancer-controller
```

### Deploy Using Scripts

#### Linux/macOS
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
- Database connection details (optional)
- Graph traversal URL (optional)

### Manual Kubernetes Deployment

```bash
# 1. Apply namespace
kubectl apply -f kubernetes/namespace.yaml

# 2. Update deployment.yaml with your image URI
# Replace {{IMAGE_URI}} with your actual image URI
sed -i 's|{{IMAGE_URI}}|123456789.dkr.ecr.us-east-1.amazonaws.com/cargo-tracker:latest|g' kubernetes/deployment.yaml

# 3. Update environment variable placeholders
sed -i 's|{{DB_JDBC_URL}}|jdbc:postgresql://your-db:5432/cargotracker|g' kubernetes/deployment.yaml
sed -i 's|{{DB_DRIVER_CLASS}}|org.postgresql.ds.PGPoolingDataSource|g' kubernetes/deployment.yaml
sed -i 's|{{DB_USER}}|postgres|g' kubernetes/deployment.yaml
sed -i 's|{{DB_PASSWORD}}|your-password|g' kubernetes/deployment.yaml
sed -i 's|{{GRAPH_TRAVERSAL_URL}}|http://cargo-tracker-service/cargo-tracker/rest/graph-traversal/shortest-path|g' kubernetes/deployment.yaml

# 4. Apply manifests
kubectl apply -f kubernetes/deployment.yaml
kubectl apply -f kubernetes/service.yaml
kubectl apply -f kubernetes/ingress.yaml

# 5. Wait for rollout
kubectl rollout status deployment/cargo-tracker -n cargo-tracker

# 6. Get application URL
kubectl get ingress cargo-tracker-ingress -n cargo-tracker
```

---

## Kubernetes Manifest Descriptions

### namespace.yaml
Creates the `cargo-tracker` namespace to isolate all application resources.

### deployment.yaml
- **Replicas**: 2 (for high availability)
- **Image**: Pulled from your container registry
- **Resources**: 250m CPU / 512Mi memory (requests), 500m CPU / 1Gi memory (limits)
- **Health Probes**: TCP socket probes on port 8080 (Jakarta EE app, no Spring Actuator)
  - Liveness: Initial delay 90s (JVM + Payara startup time)
  - Readiness: Initial delay 60s
- **JVM Options**: `-Xmx512m -Xms256m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0`

### service.yaml
- **Type**: ClusterIP (internal cluster access)
- **Port**: 80 → 8080 (container port)

### ingress.yaml
- **Controller**: AWS Load Balancer Controller (ALB)
- **Scheme**: internet-facing
- **Health Check Path**: `/cargo-tracker/`
- **Host**: `cargo-tracker.example.com` (update to your domain)

---

## Configuration Management

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `DB_JDBC_URL` | JDBC connection URL | H2 file-based |
| `DB_DRIVER_CLASS` | JDBC driver class | `org.h2.jdbcx.JdbcDataSource` |
| `DB_USER` | Database username | (empty) |
| `DB_PASSWORD` | Database password | (empty) |
| `GRAPH_TRAVERSAL_URL` | Graph traversal service URL | localhost:8080 |
| `JAVA_OPTS` | JVM options | `-Xmx512m -Xms256m ...` |
| `TZ` | Timezone | `UTC` |

### Using Kubernetes Secrets for Sensitive Data

```bash
# Create a secret for database credentials
kubectl create secret generic cargo-tracker-db-secret \
  --from-literal=db-user=postgres \
  --from-literal=db-password=your-password \
  -n cargo-tracker
```

Update `deployment.yaml` to reference the secret:
```yaml
env:
  - name: DB_USER
    valueFrom:
      secretKeyRef:
        name: cargo-tracker-db-secret
        key: db-user
  - name: DB_PASSWORD
    valueFrom:
      secretKeyRef:
        name: cargo-tracker-db-secret
        key: db-password
```

---

## Scaling and Management

### Horizontal Pod Autoscaling

```bash
# Create HPA
kubectl autoscale deployment cargo-tracker \
  --cpu-percent=70 \
  --min=2 \
  --max=10 \
  -n cargo-tracker

# Check HPA status
kubectl get hpa -n cargo-tracker
```

### Rolling Updates

```bash
# Update image
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
# Check pod status
kubectl get pods -n cargo-tracker

# Describe pod for events
kubectl describe pod <pod-name> -n cargo-tracker

# Check logs
kubectl logs <pod-name> -n cargo-tracker
kubectl logs <pod-name> -n cargo-tracker --previous  # Previous container logs
```

### Common Issues

#### 1. ImagePullBackOff
- Verify the image URI is correct
- Ensure ECR permissions are configured for the EKS node role
- Check: `kubectl describe pod <pod-name> -n cargo-tracker`

#### 2. CrashLoopBackOff
- Check application logs: `kubectl logs <pod-name> -n cargo-tracker`
- Verify environment variables are set correctly
- Ensure database is accessible from the pod
- Payara startup takes 60-90 seconds — increase `initialDelaySeconds` if needed

#### 3. Ingress Not Getting External IP
- Verify AWS Load Balancer Controller is installed
- Check controller logs: `kubectl logs -n kube-system deployment/aws-load-balancer-controller`
- Ensure EKS cluster has proper subnet tags for ALB

#### 4. Database Connection Issues
- Verify `DB_JDBC_URL` is correct and accessible from the cluster
- Check security groups allow traffic from EKS nodes to the database
- Test connectivity: `kubectl exec -it <pod-name> -n cargo-tracker -- /bin/bash`

#### 5. Application Returns 404
- Verify context root: application is deployed at `/cargo-tracker`
- Check Payara deployment logs
- Verify the WAR was built successfully

### Useful Diagnostic Commands

```bash
# Get all resources in namespace
kubectl get all -n cargo-tracker

# Check ingress details
kubectl describe ingress cargo-tracker-ingress -n cargo-tracker

# Port-forward for local testing
kubectl port-forward svc/cargo-tracker-service 8080:80 -n cargo-tracker
# Then access: http://localhost:8080/cargo-tracker

# Execute shell in pod
kubectl exec -it deployment/cargo-tracker -n cargo-tracker -- /bin/bash

# Check resource usage
kubectl top pods -n cargo-tracker
```

---

## Security Considerations

1. **Non-root User**: The container runs as the `payara` user (non-root)
2. **Secrets Management**: Use Kubernetes Secrets or AWS Secrets Manager for sensitive data
3. **Network Policies**: Consider adding Kubernetes NetworkPolicies to restrict pod-to-pod communication
4. **Image Scanning**: Enable ECR image scanning for vulnerability detection
5. **RBAC**: Apply least-privilege RBAC policies for the application's service account
6. **TLS**: Configure HTTPS on the ALB ingress using ACM certificates:
   ```yaml
   annotations:
     alb.ingress.kubernetes.io/certificate-arn: arn:aws:acm:us-east-1:123456789:certificate/xxx
     alb.ingress.kubernetes.io/listen-ports: '[{"HTTPS":443}]'
   ```

---

## Java/Jakarta EE Specific Notes

### JVM Configuration
The application uses the following JVM flags for container optimization:
- `-XX:+UseContainerSupport`: Enables container-aware memory detection
- `-XX:MaxRAMPercentage=75.0`: Limits heap to 75% of container memory
- `-Xmx512m -Xms256m`: Explicit heap bounds

### Payara Server
- The application runs on Payara Server 6.x (Jakarta EE 10 compatible)
- JMS queues are configured internally within Payara
- H2 database is embedded for development; PostgreSQL is recommended for production

### Database Profiles
- **Development (default)**: H2 file-based database (no external dependency)
- **Cloud/Production**: PostgreSQL via the `cloud` Maven profile

### Application Context
- The application is deployed at context root `/cargo-tracker`
- REST API is available at `/cargo-tracker/rest/`
- Graph traversal service: `/cargo-tracker/rest/graph-traversal/shortest-path`

---

## Support

For issues and questions:
- GitHub Issues: https://github.com/eclipse-ee4j/cargotracker/issues
- Eclipse Cargo Tracker: https://eclipse-ee4j.github.io/cargotracker/
