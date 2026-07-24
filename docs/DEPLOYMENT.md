# Deployment Guide for Cargo Tracker on AWS EKS

This guide provides instructions for containerizing and deploying the Cargo Tracker application to AWS Elastic Kubernetes Service (EKS).

## Prerequisites

### System Requirements
- Docker installed and running
- AWS CLI installed and configured with appropriate IAM permissions
- `kubectl` installed
- Java 11 JDK (for local builds)
- Maven 3.9+ (for local builds)

### AWS EKS Requirements
- An existing AWS EKS Cluster
- AWS Load Balancer Controller installed in the cluster (for Ingress/ALB)
- IAM role allowing the EKS nodes to pull images from ECR

## Local Development Setup

### Using Docker Compose
For local testing, you can use the provided `docker-compose.yml`. Note that you will need a running PostgreSQL instance.

1. Configure environment variables in `docker-compose.yml` to point to your local database.
2. Run the application:
   ```bash
   docker-compose up -d
   ```
3. Access the application at `http://localhost:8080/cargo-tracker`.

## Build and Push Process

### 1. Build the Docker Image
Use the provided scripts to build and push the image to your chosen registry.

**Linux/macOS:**
```bash
chmod +x scripts/build-push.sh
./scripts/build-push.sh
```

**Windows:**
```cmd
scripts\build-push.bat
```

The script will prompt you for:
- Image tag (default: `latest`)
- Registry type (AWS ECR or Docker Hub)
- Registry details (Region, Repository name, etc.)

### 2. Push to Registry
The build script automatically handles authentication and pushes the image to the selected registry.

## AWS EKS Deployment

### 1. Deployment Walkthrough
Use the deployment script to automate the process of updating manifests and applying them to the cluster.

**Linux/macOS:**
```bash
chmod +x scripts/deploy-image.sh
./scripts/deploy-image.sh
```

**Windows:**
```cmd
scripts\deploy-image.bat
```

The script will prompt for:
- AWS Region and EKS Cluster Name
- Full Docker Image URI
- Database connection details (JDBC URL, Username, Password)

### 2. Manifest Descriptions
- `kubernetes/namespace.yaml`: Creates a dedicated namespace `cargotracker` for isolation.
- `kubernetes/deployment.yaml`: Defines the application pods, resource limits (CPU: 500m, Memory: 1Gi), and health probes.
- `kubernetes/service.yaml`: Exposes the application internally within the cluster via ClusterIP.
- `kubernetes/ingress.yaml`: Configures an AWS Application Load Balancer (ALB) to route external traffic to the service.

## Troubleshooting

### Pod Failures
- Check pod logs: `kubectl logs -l app=cargotracker -n cargotracker`
- Describe pod for events: `kubectl describe pod -l app=cargotracker -n cargotracker`

### Service/Ingress Issues
- Verify the ALB is created: `kubectl get ingress -n cargotracker`
- Check if the service is targeting the correct pods: `kubectl get endpoints cargotracker-service -n cargotracker`

### Database Connectivity
- Ensure the PostgreSQL database is accessible from the EKS cluster.
- Verify the `POSTGRESQL_JDBC_URL` provided during deployment is correct.

## Configuration Management
The application uses environment variables for configuration. These are injected into the deployment via the `deploy-image` scripts.

## Security Considerations
- **Non-Root User**: The Docker image runs as a non-root user (`appuser`) for security.
- **Resource Limits**: CPU and Memory limits are enforced to prevent resource exhaustion.
- **JVM Optimization**: The image uses `-XX:+UseContainerSupport` and `MaxRAMPercentage` for optimal memory management in containers.
