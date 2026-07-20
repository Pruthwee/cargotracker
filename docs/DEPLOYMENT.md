# Deployment Guide - Cargo Tracker

This guide provides instructions for deploying the Cargo Tracker application using Docker and AWS EKS.

## Prerequisites

### System Requirements
- Java 11 JDK
- Maven 3.9+
- Docker installed and running
- AWS CLI configured with appropriate permissions
- `kubectl` installed

### AWS EKS Requirements
- An active AWS Account
- EKS Cluster (or permissions to create one)
- AWS Load Balancer Controller installed in the cluster (for Ingress)

## Local Development Setup

### Using Docker Compose
1. Create a `.env` file or set environment variables:
   ```bash
   export DB_JDBC_URL=jdbc:postgresql://localhost:5432/cargotracker
   export DB_USER=postgres
   export DB_PASSWORD=postgres
   ```
2. Start the application:
   ```bash
   docker-compose up --build
   ```
3. Access the application at `http://localhost:8080/cargo-tracker`

## Build and Push Instructions

### Linux/macOS
```bash
chmod +x scripts/build-push.sh
./scripts/build-push.sh
```

### Windows
```cmd
scripts\build-push.bat
```

The script will prompt you to choose between AWS ECR and Docker Hub and will handle the build and push process.

## AWS EKS Deployment Walkthrough

### 1. Cluster Configuration
Ensure your `kubectl` context is set to the correct EKS cluster. The deployment script handles this automatically using `aws eks update-kubeconfig`.

### 2. Deployment Execution
Run the deployment script:

**Linux/macOS:**
```bash
chmod +x scripts/deploy-image.sh
./scripts/deploy-image.sh
```

**Windows:**
```cmd
scripts\deploy-image.bat
```

### 3. Manifest Descriptions
- `namespace.yaml`: Creates a dedicated `cargo-tracker` namespace.
- `deployment.yaml`: Manages the application pods, including resource limits and health probes.
- `service.yaml`: Exposes the application internally within the cluster.
- `ingress.yaml`: Configures the AWS Application Load Balancer to route external traffic to the service.

## Troubleshooting

### Pod Failures
Check pod logs:
```bash
kubectl logs -l app=cargo-tracker -n cargo-tracker
```
Describe the pod for events:
```bash
kubectl describe pod <pod-name> -n cargo-tracker
```

### Service/Ingress Issues
Verify the service is targeting the correct pods:
```bash
kubectl get endpoints -n cargo-tracker
```
Check the ALB status via the AWS Console or `kubectl get ingress -n cargo-tracker`.

## Configuration Management
The application uses environment variables for configuration. These are injected into the Kubernetes deployment via the `deploy-image.sh/bat` scripts.

## Security Considerations
- The Docker image runs as a non-root user (`appuser`).
- Resource limits are set to prevent noisy neighbor issues in the cluster.
- Use AWS Secrets Manager or Kubernetes Secrets for sensitive data like `DB_PASSWORD` in production.

## Java Specific Notes
- **JVM Memory**: The application is configured with `-Xmx512m -Xms256m` and `MaxRAMPercentage=75.0` to ensure it respects container limits.
- **Timezone**: Set to `UTC` for consistency across distributed environments.
