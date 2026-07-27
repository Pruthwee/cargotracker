#!/bin/bash
set -e
set -o pipefail

PROJECT_NAME="cargo-tracker"
NAMESPACE="cargo-tracker"

echo "-------------------------------------------------------"
echo "Deploying $PROJECT_NAME to AWS EKS"
echo "-------------------------------------------------------"

# Prompt for AWS and EKS details
read -p "Enter AWS Region [us-east-1]: " AWS_REGION
AWS_REGION=${AWS_REGION:-us-east-1}
read -p "Enter EKS Cluster Name: " CLUSTER_NAME
if [ -z "$CLUSTER_NAME" ]; then
    echo "Cluster name is required."
    exit 1
fi

# Prompt for Docker Image URI
read -p "Enter full Docker Image URI (e.g., 123456789012.dkr.ecr.us-east-1.amazonaws.com/cargo-tracker:latest): " IMAGE_URI
if [ -z "$IMAGE_URI" ]; then
    echo "Image URI is required."
    exit 1
fi

# Prompt for application-specific environment variables
echo "Enter application configuration (press Enter to skip):"
read -p "DB_JDBC_URL [jdbc:postgresql://postgres:5432/cargo]: " DB_JDBC_URL
DB_JDBC_URL=${DB_JDBC_URL:-jdbc:postgresql://postgres:5432/cargo}
read -p "DB_USER [postgres]: " DB_USER
DB_USER=${DB_USER:-postgres}
read -p "DB_PASSWORD: " DB_PASSWORD

# Update Kubernetes manifests
echo "Updating manifests..."
sed -i "s|{{IMAGE_URI}}|$IMAGE_URI|g" kubernetes/deployment.yaml
sed -i "s|{{DB_JDBC_URL}}|$DB_JDBC_URL|g" kubernetes/deployment.yaml
sed -i "s|{{DB_USER}}|$DB_USER|g" kubernetes/deployment.yaml
sed -i "s|{{DB_PASSWORD}}|$DB_PASSWORD|g" kubernetes/deployment.yaml

# Configure kubectl
echo "Configuring kubectl for EKS..."
aws eks update-kubeconfig --region $AWS_REGION --name $CLUSTER_NAME

# Verify connectivity
echo "Verifying cluster connectivity..."
kubectl cluster-info || { echo "Failed to connect to EKS cluster"; exit 1; }

# Apply manifests in order
echo "Applying manifests..."
kubectl apply -f kubernetes/namespace.yaml
kubectl apply -f kubernetes/deployment.yaml
kubectl apply -f kubernetes/service.yaml
kubectl apply -f kubernetes/ingress.yaml

# Wait for rollout
echo "Waiting for deployment rollout..."
kubectl rollout status deployment/$PROJECT_NAME -n $NAMESPACE

# Verify resources
echo "Verifying resources..."
kubectl get pods,svc,ingress -n $NAMESPACE

echo "-------------------------------------------------------"
echo "Deployment complete!"
echo "Application should be accessible via the Ingress URL."
echo "To rollback in case of failure: kubectl rollout undo deployment/$PROJECT_NAME -n $NAMESPACE"
echo "-------------------------------------------------------"
