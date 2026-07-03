# Deployment Guide for Cargo Tracker on AWS EKS

## Overview
This guide provides instructions for containerizing and deploying the Cargo Tracker application to AWS Elastic Kubernetes Service (EKS).

## Prerequisites
- Java 11 JDK
- Maven 3.9+
- Docker
- AWS CLI configured with appropriate IAM permissions
- kubectl installed
- AWS EKS Cluster

## Local Development Setup
1. Clone the repository.
2. Use Docker Compose for local testing:
   ```bash
   docker-compose up -d
   ```
3. Access the application at `http://localhost:8080`.

## Build and Push Instructions
1. Run the build script:
   - Linux/macOS: `./scripts/build-push.sh`
   - Windows: `scripts\\build-push.bat`
2. Follow the prompts to select your registry (AWS ECR or Docker Hub) and provide credentials.
3. The script will build the image and push it to the selected registry.

## AWS EKS Deployment
1. Ensure your AWS CLI is configured for the correct account and region.
2. Run the deployment script:
   - Linux/macOS: `./scripts/deploy-image.sh`
   - Windows: `scripts\\deploy-image.bat`
3. Provide the EKS cluster name, region, and the full Docker image URI.
4. Enter the required environment variables (e.g., `DB_JDBC_URL`).

## Kubernetes Manifests
- `kubernetes/namespace.yaml`: Creates the `cargo-tracker` namespace.
- `kubernetes/deployment.yaml`: Defines the application pods, resource limits, and health probes.
- `kubernetes/service.yaml`: Exposes the application internally within the cluster.
- `kubernetes/ingress.yaml`: Configures the AWS Application Load Balancer (ALB) for external access.

## Troubleshooting
- **Pod Failures**: Check logs using `kubectl logs -l app=cargo-tracker -n cargo-tracker`.
- **Service Issues**: Verify service selector matches deployment labels.
- **Ingress Problems**: Ensure the AWS Load Balancer Controller is installed in the cluster.

## Configuration Management
Environment variables are used to configure the application:
- `DB_JDBC_URL`: Connection string for the database.
- `DB_USER`: Database username.
- `DB_PASSWORD`: Database password.
- `JAVA_OPTS`: JVM tuning parameters.

## Security Considerations
- The application runs as a non-root user (`appuser`) in the container.
- Resource limits are set to prevent noisy neighbor issues.
- Use AWS Secrets Manager or Kubernetes Secrets for sensitive data in production.
