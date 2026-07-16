# Deployment Guide - Cargo Tracker

This guide provides instructions for containerizing and deploying the Cargo Tracker application to AWS EKS.

## Prerequisites

### System Requirements
- Java 11 JDK
- Maven 3.9+
- Docker
- AWS CLI configured with appropriate permissions
- kubectl installed

### AWS EKS Requirements
- An existing EKS Cluster or permissions to create one
- AWS Load Balancer Controller installed in the cluster (for Ingress)

## Local Development Setup

### Using Docker Compose
1. Ensure you have a database running (PostgreSQL) or use the default H2.
2. Run the following command:
   ```bash
   docker-compose up --build
   ```
3. The application will be available at `http://localhost:8080/cargo-tracker`.

## Build and Push Process

### Build and Push Image
Use the provided scripts to build the Docker image and push it to a registry (AWS ECR or Docker Hub).

**Linux/macOS:**
```bash
chmod +x scripts/build-push.sh
./scripts/build-push.sh
```

**Windows:**
```cmd
scripts\build-push.bat
```

## AWS EKS Deployment

### Deployment Steps
1. Ensure your AWS CLI is authenticated.
2. Run the deployment script:

**Linux/macOS:**
```bash
chmod +x scripts/deploy-image.sh
./scripts/deploy-image.sh
```

**Windows:**
```cmd
scripts\deploy-image.bat
```

### Manifest Descriptions
- `namespace.yaml`: Creates a dedicated namespace `cargotracker` for isolation.
- `deployment.yaml`: Defines the application pods, resource limits (500m CPU, 1Gi RAM), and health probes.
- `service.yaml`: Exposes the application internally within the cluster.
- `ingress.yaml`: Configures an AWS Application Load Balancer to route external traffic to the service.

## Troubleshooting

### Pod Failures
- Check logs: `kubectl logs -l app=cargotracker -n cargotracker`
- Describe pod: `kubectl describe pod <pod-name> -n cargotracker`

### Ingress Issues
- Verify AWS Load Balancer Controller is running.
- Check ingress events: `kubectl describe ingress cargotracker-ingress -n cargotracker`

## Configuration Management
The application uses environment variables for configuration. These are prompted during the `deploy-image` script execution and injected into the Kubernetes deployment.

- `DB_JDBC_URL`: Connection string for the database.
- `DB_USER`: Database username.
- `DB_PASSWORD`: Database password.

## Security Considerations
- The container runs as a non-root user (`appuser`).
- Resource limits are enforced to prevent noisy neighbor issues.
- Secrets should be managed using AWS Secrets Manager or Kubernetes Secrets in a production environment.
