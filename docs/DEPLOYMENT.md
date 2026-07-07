# Eclipse Cargo Tracker - Deployment Guide

## Overview

This guide covers the complete deployment process for the **Eclipse Cargo Tracker** application on **AWS EKS (Elastic Kubernetes Service)**. The application is a Jakarta EE 10 web application built with Maven, packaged as a WAR, and deployed on Payara Micro.

---

## Technology Stack

| Component        | Details                                      |
|-----------------|----------------------------------------------|
| Language         | Java 11                                      |
| Framework        | Jakarta EE 10 (CDI, JPA, JAX-RS, JSF, JMS)  |
| Build Tool       | Maven 3.9.x                                  |
| Packaging        | WAR                                          |
| Runtime          | Payara Micro 6.2025.3                        |
| Base Image       | amazoncorretto:11                            |
| Application Port | 8080 (HTTP), 8181 (HTTPS)                    |
| Database         | H2 (embedded, default) / PostgreSQL (cloud)  |

---

## Prerequisites

### Local Development
- Java 11 JDK
- Maven 3.9+
- Docker Desktop (or Docker Engine)
- Docker Compose v2+

### AWS EKS Deployment
- AWS CLI v2 (`aws --version`)
- kubectl (`kubectl version --client`)
- eksctl (optional, for cluster creation)
- AWS IAM permissions:
  - `eks:DescribeCluster`
  - `eks:UpdateKubeconfig`
  - `ecr:GetAuthorizationToken`
  - `ecr:BatchCheckLayerAvailability`
  - `ecr:PutImage`
  - `ecr:InitiateLayerUpload`
  - `ecr:UploadLayerPart`
  - `ecr:CompleteLayerUpload`
  - `ecr:CreateRepository`
  - `ecr:DescribeRepositories`

---

## Project Structure

```
cargotracker/
├── Dockerfile                    # Multi-stage Docker build
├── docker-compose.yml            # Local development compose file
├── .dockerignore                 # Docker build exclusions
├── pom.xml                       # Maven build descriptor
├── post-boot-commands.asadmin    # Payara post-boot configuration
├── src/
│   ├── main/
│   │   ├── java/                 # Application source code
│   │   ├── webapp/               # JSF web resources
│   │   └── liberty/config/       # OpenLiberty configuration
│   └── test/                     # Test sources
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

## Local Development with Docker Compose

### Step 1: Build the Docker Image

```bash
docker build -t cargo-tracker:latest .
```

### Step 2: Start the Application

```bash
docker-compose up -d
```

### Step 3: Access the Application

- **HTTP**: http://localhost:8080/
- **HTTPS**: https://localhost:8181/

### Step 4: View Logs

```bash
docker-compose logs -f cargo-tracker
```

### Step 5: Stop the Application

```bash
docker-compose down
```

---

## Building and Pushing the Docker Image

### Linux/macOS

```bash
chmod +x scripts/build-push.sh
./scripts/build-push.sh
```

### Windows

```cmd
scripts\build-push.bat
```

The script will prompt you to:
1. Select registry type (AWS ECR or Docker Hub)
2. Enter registry credentials and details
3. Enter an image tag (defaults to `latest`)

### Manual Build (AWS ECR Example)

```bash
# Authenticate with ECR
aws ecr get-login-password --region us-east-1 | \
  docker login --username AWS --password-stdin \
  123456789012.dkr.ecr.us-east-1.amazonaws.com

# Create ECR repository (if not exists)
aws ecr create-repository --repository-name cargo-tracker --region us-east-1

# Build and push
docker build -t 123456789012.dkr.ecr.us-east-1.amazonaws.com/cargo-tracker:latest .
docker push 123456789012.dkr.ecr.us-east-1.amazonaws.com/cargo-tracker:latest
```

---

## AWS EKS Deployment

### Step 1: Create EKS Cluster (if not exists)

```bash
eksctl create cluster \
  --name cargo-tracker-cluster \
  --region us-east-1 \
  --nodegroup-name standard-workers \
  --node-type t3.medium \
  --nodes 2 \
  --nodes-min 1 \
  --nodes-max 4 \
  --managed
```

### Step 2: Install AWS Load Balancer Controller

The ingress uses the AWS Load Balancer Controller. Install it on your cluster:

```bash
# Add the EKS chart repo
helm repo add eks https://aws.github.io/eks-charts
helm repo update

# Install the controller
helm install aws-load-balancer-controller eks/aws-load-balancer-controller \
  -n kube-system \
  --set clusterName=cargo-tracker-cluster \
  --set serviceAccount.create=false \
  --set serviceAccount.name=aws-load-balancer-controller
```

> **Note**: Ensure the IAM role for the AWS Load Balancer Controller is configured. See [AWS documentation](https://docs.aws.amazon.com/eks/latest/userguide/aws-load-balancer-controller.html).

### Step 3: Configure kubectl

```bash
aws eks update-kubeconfig --region us-east-1 --name cargo-tracker-cluster
kubectl cluster-info
```

### Step 4: Deploy Using Script

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
- Application configuration (GRAPH_TRAVERSAL_URL, DB_JDBC_URL, etc.)

### Step 5: Manual Deployment

If you prefer to deploy manually:

```bash
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

## Kubernetes Manifest Descriptions

### namespace.yaml
Creates the `cargo-tracker` namespace to isolate all application resources.

### deployment.yaml
Deploys 2 replicas of the cargo-tracker container with:
- Rolling update strategy (zero downtime)
- Resource limits: CPU 500m, Memory 1Gi
- Resource requests: CPU 250m, Memory 512Mi
- TCP socket liveness and readiness probes on port 8080
- JVM tuning via `JAVA_OPTS` environment variable
- Non-root security context

### service.yaml
Creates a `ClusterIP` service exposing:
- Port 80 → Container port 8080 (HTTP)
- Port 443 → Container port 8181 (HTTPS)

### ingress.yaml
Creates an AWS ALB Ingress with:
- Internet-facing scheme
- HTTP to HTTPS redirect
- Session stickiness (cookie-based, 48-hour duration)
- Health check on path `/`

---

## Configuration Management

### Environment Variables

| Variable              | Description                          | Default                                                                 |
|-----------------------|--------------------------------------|-------------------------------------------------------------------------|
| `TZ`                  | Timezone                             | `UTC`                                                                   |
| `JAVA_OPTS`           | JVM options                          | `-Xmx512m -Xms256m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0` |
| `GRAPH_TRAVERSAL_URL` | Graph traversal REST endpoint URL    | `http://localhost:8080/cargo-tracker/rest/graph-traversal/shortest-path`|
| `DB_JDBC_URL`         | JDBC URL for the database            | `jdbc:h2:file:./cargo-tracker-data/cargo-tracker-database`              |
| `DB_USER`             | Database username                    | *(empty)*                                                               |
| `DB_PASSWORD`         | Database password (from secret)      | *(empty)*                                                               |

### Using Kubernetes Secrets for Sensitive Data

```bash
kubectl create secret generic cargo-tracker-secrets \
  --from-literal=db-password=your-db-password \
  -n cargo-tracker
```

---

## Scaling

### Manual Scaling

```bash
kubectl scale deployment cargo-tracker --replicas=3 -n cargo-tracker
```

### Horizontal Pod Autoscaler (HPA)

```bash
kubectl autoscale deployment cargo-tracker \
  --cpu-percent=70 \
  --min=2 \
  --max=10 \
  -n cargo-tracker
```

---

## Rolling Updates

```bash
# Update the image
kubectl set image deployment/cargo-tracker \
  cargo-tracker=123456789012.dkr.ecr.us-east-1.amazonaws.com/cargo-tracker:v2.0 \
  -n cargo-tracker

# Monitor rollout
kubectl rollout status deployment/cargo-tracker -n cargo-tracker
```

---

## Rollback

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

# View pod logs
kubectl logs <pod-name> -n cargo-tracker --tail=100

# View previous container logs (if crashed)
kubectl logs <pod-name> -n cargo-tracker --previous
```

### Service Not Accessible

```bash
# Check service endpoints
kubectl get endpoints cargo-tracker-service -n cargo-tracker

# Test service from within cluster
kubectl run test-pod --image=busybox --rm -it --restart=Never -- \
  wget -qO- http://cargo-tracker-service.cargo-tracker.svc.cluster.local/
```

### Ingress Not Provisioning

```bash
# Check ingress status
kubectl describe ingress cargo-tracker-ingress -n cargo-tracker

# Check AWS Load Balancer Controller logs
kubectl logs -n kube-system -l app.kubernetes.io/name=aws-load-balancer-controller
```

### JVM Memory Issues

If pods are OOMKilled, increase memory limits in `kubernetes/deployment.yaml`:

```yaml
resources:
  requests:
    memory: "1Gi"
  limits:
    memory: "2Gi"
```

Also adjust `JAVA_OPTS`:
```yaml
- name: JAVA_OPTS
  value: "-Xmx1g -Xms512m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"
```

### Payara Micro Startup Issues

Payara Micro requires significant startup time (60-120 seconds). If readiness probes fail:

```yaml
readinessProbe:
  initialDelaySeconds: 120  # Increase if needed
  periodSeconds: 15
  failureThreshold: 5       # Increase tolerance
```

---

## Security Considerations

1. **Non-root user**: The container runs as user `payara` (UID 1000) — never run as root.
2. **Secrets management**: Use Kubernetes Secrets or AWS Secrets Manager for sensitive data.
3. **Network policies**: Consider adding Kubernetes NetworkPolicies to restrict pod-to-pod communication.
4. **Image scanning**: Enable ECR image scanning to detect vulnerabilities.
5. **RBAC**: Apply least-privilege RBAC policies for service accounts.
6. **TLS**: Configure TLS certificates via AWS Certificate Manager (ACM) for the ALB.

---

## Java-Specific Notes

### JVM Container Awareness
The JVM is configured with `-XX:+UseContainerSupport` and `-XX:MaxRAMPercentage=75.0` to respect container memory limits automatically.

### Payara Micro
- Payara Micro is a self-contained Jakarta EE runtime that embeds the application server.
- The WAR is deployed at context root `/`.
- JMS queues are configured internally within Payara Micro.
- H2 database is used by default (embedded, file-based). For production, switch to PostgreSQL using the `cloud` Maven profile.

### Production Database
For production deployments, use PostgreSQL:

```bash
mvn clean package -Pcloud \
  -DpostgreSqlJdbcUrl="jdbc:postgresql://your-rds-host:5432/cargotracker" \
  -DpostgreSqlUsername="postgres" \
  -DpostgreSqlPassword="your-password"
```

Update the `DB_JDBC_URL`, `DB_USER`, and `DB_PASSWORD` environment variables in the Kubernetes deployment accordingly.

---

## Support

- **Project Repository**: https://github.com/eclipse-ee4j/cargotracker
- **Issue Tracker**: https://github.com/eclipse-ee4j/cargotracker/issues
- **Jakarta EE Documentation**: https://jakarta.ee/
- **Payara Documentation**: https://docs.payara.fish/
