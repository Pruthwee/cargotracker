#!/bin/bash
set -e
set -o pipefail

echo "-------------------------------------------------------"
echo "Cargo Tracker - Deploy to AWS EKS"
echo "-------------------------------------------------------"

# Prompt for AWS and EKS details
read -p "Enter AWS Region [us-east-1]: " AWS_REGION
AWS_REGION=${AWS_REGION:-us-east-1}
read -p "Enter EKS Cluster Name: " CLUSTER_NAME
if [ -z "$CLUSTER_NAME" ]; then
    echo "Error: Cluster name is required."
    exit 1
fi

# Prompt for Docker Image URI
read -p "Enter Docker Image URI (e.g., 123456789012.dkr.ecr.us-east-1.amazonaws.com/cargo-tracker:latest): " IMAGE_URI
if [ -z "$IMAGE_URI" ]; then
    echo "Error: Image URI is required."
    exit 1
fi

# Prompt for Application Environment Variables
echo "Enter application configuration (press Enter to skip):"
read -p "DB_JDBC_URL: " DB_JDBC_URL
read -p "DB_USER: " DB_USER
read -p "DB_PASSWORD: " DB_PASSWORD

# Update Kubernetes manifests
echo "Updating manifests with provided values..."
sed -i "s|{{IMAGE_URI}}|$IMAGE_URI|g" kubernetes/deployment.yaml
sed -i "s|{{DB_JDBC_URL}}|$DB_JDBC_URL|g" kubernetes/deployment.yaml
sed -i "s|{{DB_USER}}|$DB_USER|g" kubernetes/deployment.yaml
sed -i "s|{{DB_PASSWORD}}|$DB_PASSWORD|g" kubernetes/deployment.yaml

# Configure kubectl
echo "Configuring kubectl for EKS cluster $CLUSTER_NAME..."
aws eks update-kubeconfig --region $AWS_REGION --name $CLUSTER_NAME

# Verify connectivity
echo "Verifying cluster connectivity..."
kubectl cluster-info || { echo "Error: Could not connect to EKS cluster"; exit 1; }

# Apply manifests in order
echo "Applying Kubernetes manifests..."
kubectl apply -f kubernetes/namespace.yaml
kubectl apply -f kubernetes/deployment.yaml
kubectl apply -f kubernetes/service.yaml
kubectl apply -f kubernetes/ingress.yaml

# Wait for rollout
echo "Waiting for deployment rollout..."
kubectl rollout status deployment/cargo-tracker -n cargo-tracker

# Verify resources
echo "Verifying deployed resources..."
kubectl get pods,svc,ingress -n cargo-tracker

echo "-------------------------------------------------------"
echo "Deployment Complete!"
echo "Application should be available at cargo-tracker.example.com"
echo "-------------------------------------------------------"
echo "Rollback instructions: kubectl rollout undo deployment/cargo-tracker -n cargo-tracker"
