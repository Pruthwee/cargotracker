# Deployment Guide for Cargo Tracker on AWS EKS

This guide provides instructions for containerizing and deploying the Cargo Tracker application to AWS Elastic Kubernetes Service (EKS).

## 1. Prerequisites

### System Requirements
- Docker installed and running
- AWS CLI installed and configured with appropriate IAM permissions
- `kubectl` installed
- Java 11 JDK (for local builds)
- Maven 3.9+ (for local builds)

### AWS EKS Requirements
- An active AWS Account
- An existing EKS Cluster (or instructions to create one using `eksctl`)
- AWS Load Balancer Controller installed in the EKS cluster (for Ingress to work)

## 2. Local Development Setup

### Using Docker Compose
For quick local testing, you can use the provided `docker-compose.yml`:

1. Create a `config` directory in the project root.
2. Run the application:
   ```bash
   docker-compose up --build
   ```
3. The application will be available at `http://localhost:8080/cargo-tracker`.

## 3. Build and Push Image

### Linux/macOS
```bash
chmod +x scripts/build-push.sh
./scripts/build-push.sh
```

### Windows
```cmd
scripts\build-push.bat
```

The script will prompt you to choose between AWS ECR and Docker Hub. If ECR is chosen, it will automatically create the repository if it doesn't exist.

## 4. AWS EKS Deployment

### Deployment Steps
1. Ensure your AWS CLI is configured for the correct region.
2. Run the deployment script:
   - **Linux/macOS**: `./scripts/deploy-image.sh`
   - **Windows**: `scripts\deploy-image.bat`
3. Provide the following when prompted:
   - AWS Region
   - EKS Cluster Name
   - Full Docker Image URI (from the build-push step)
   - Database connection details (JDBC URL, Username, Password)

### Manifests Overview
- `kubernetes/namespace.yaml`: Creates a dedicated namespace `cargo-tracker`.
- `kubernetes/deployment.yaml`: Defines the application pods, resource limits, and health probes.
- `kubernetes/service.yaml`: Exposes the application internally within the cluster.
- `kubernetes/ingress.yaml`: Configures the AWS Application Load Balancer (ALB) to route external traffic to the service.

## 5. Troubleshooting

### Pod Failures
Check pod logs:
```bash
kubectl logs -l app=cargo-tracker -n cargo-tracker
```
Describe the pod for events:
```bash
kubectl describe pod <pod-name> -n cargo-tracker
```

### Ingress Issues
Verify the ALB is created:
```bash
kubectl get ingress -n cargo-tracker
```
Ensure the AWS Load Balancer Controller is running in the `kube-system` namespace.

### Database Connectivity
If the application fails to start due to database issues, verify that the `DB_JDBC_URL` provided during deployment is reachable from within the EKS cluster.

## 6. Configuration Management

The application uses environment variables for configuration:
- `DB_JDBC_URL`: The JDBC connection string for the database.
- `DB_USER`: Database username.
- `DB_PASSWORD`: Database password.
- `JAVA_OPTS`: JVM memory and performance settings.

## 7. Security Considerations
- The container runs as a non-root user (`appuser`).
- Resource limits are enforced to prevent cluster instability.
- Use AWS Secrets Manager or Kubernetes Secrets for sensitive data like `DB_PASSWORD` in production.
