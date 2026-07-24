#!/bin/bash
set -e
set -o pipefail

PROJECT_NAME="cargotracker"

echo "-------------------------------------------------------"
echo "Deploying $PROJECT_NAME to AWS EKS"
echo "-------------------------------------------------------"

# AWS Configuration
read -p "Enter AWS Region [us-east-1]: " AWS_REGION
AWS_REGION=${AWS_REGION:-us-east-1}
read -p "Enter EKS Cluster Name: " CLUSTER_NAME

if [ -z "$CLUSTER_NAME" ]; then
    echo "Error: Cluster name is required."
    exit 1
fi

# Image URI
read -p "Enter Docker Image URI (e.g., 123456789012.dkr.ecr.us-east-1.amazonaws.com/cargotracker:latest): " IMAGE_URI
if [ -z "$IMAGE_URI" ]; then
    echo "Error: Image URI is required."
    exit 1
fi

# Application Environment Variables
echo "Enter application configuration (press Enter to skip):"
read -p "POSTGRESQL_JDBC_URL [jdbc:postgresql://postgres-db:5432/cargotracker]: " POSTGRESQL_JDBC_URL
POSTGRESQL_JDBC_URL=${POSTGRESQL_JDBC_URL:-jdbc:postgresql://postgres-db:5432/cargotracker}

read -p "POSTGRESQL_USERNAME [postgres]: " POSTGRESQL_USERNAME
POSTGRESQL_USERNAME=${POSTGRESQL_USERNAME:-postgres}

read -p "POSTGRESQL_PASSWORD: " POSTGRESQL_PASSWORD

# Update Manifests
echo "Updating Kubernetes manifests..."
sed -i "s|{{IMAGE_URI}}|$IMAGE_URI|g" kubernetes/deployment.yaml
sed -i "s|{{POSTGRESQL_JDBC_URL}}|$POSTGRESQL_JDBC_URL|g" kubernetes/deployment.yaml
sed -i "s|{{POSTGRESQL_USERNAME}}|$POSTGRESQL_USERNAME|g" kubernetes/deployment.yaml
sed -i "s|{{POSTGRESQL_PASSWORD}}|$POSTGRESQL_PASSWORD|g" kubernetes/deployment.yaml

# Configure kubectl
echo "Configuring kubectl for EKS..."
aws eks update-kubeconfig --region "$AWS_REGION" --name "$CLUSTER_NAME"

# Verify connectivity
echo "Verifying cluster connectivity..."
kubectl cluster-info || { echo "Error: Cannot connect to EKS cluster"; exit 1; }

# Apply Manifests
echo "Applying Kubernetes manifests..."
kubectl apply -f kubernetes/namespace.yaml
kubectl apply -f kubernetes/deployment.yaml
kubectl apply -f kubernetes/service.yaml
kubectl apply -f kubernetes/ingress.yaml

# Wait for rollout
echo "Waiting for deployment rollout..."
kubectl rollout status deployment/$PROJECT_NAME -n $PROJECT_NAME

# Verify resources
echo "Verifying resources..."
kubectl get pods,svc,ingress -n $PROJECT_NAME

echo "-------------------------------------------------------"
echo "Deployment successful!"
echo "Application is available via the Ingress URL (check AWS Console for ALB DNS)."
echo "-------------------------------------------------------"
echo "To rollback in case of failure: kubectl rollout undo deployment/$PROJECT_NAME -n $PROJECT_NAME"
