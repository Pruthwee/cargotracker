#!/bin/bash
set -e
set -o pipefail

PROJECT_NAME="cargo-tracker"

echo "--- AWS EKS Deployment ---"
read -p "Enter AWS Region (e.g., us-east-1): " AWS_REGION
read -p "Enter EKS Cluster Name: " CLUSTER_NAME
read -p "Enter Docker Image URI (full path with tag): " IMAGE_URI

# Application specific environment variables
read -p "Enter DB_JDBC_URL (or press Enter to skip): " DB_JDBC_URL
read -p "Enter DB_USER (or press Enter to skip): " DB_USER
read -p "Enter DB_PASSWORD (or press Enter to skip): " DB_PASSWORD

# Update manifests
sed -i "s|{{IMAGE_URI}}|$IMAGE_URI|g" kubernetes/deployment.yaml
sed -i "s|{{DB_JDBC_URL}}|$DB_JDBC_URL|g" kubernetes/deployment.yaml
sed -i "s|{{DB_USER}}|$DB_USER|g" kubernetes/deployment.yaml
sed -i "s|{{DB_PASSWORD}}|$DB_PASSWORD|g" kubernetes/deployment.yaml

echo "Configuring kubectl..."
aws eks update-kubeconfig --region $AWS_REGION --name $CLUSTER_NAME

echo "Verifying cluster connectivity..."
kubectl cluster-info || { echo "Cluster connectivity failed"; exit 1; }

echo "Applying manifests..."
kubectl apply -f kubernetes/namespace.yaml
kubectl apply -f kubernetes/deployment.yaml
kubectl apply -f kubernetes/service.yaml
kubectl apply -f kubernetes/ingress.yaml

echo "Waiting for rollout..."
kubectl rollout status deployment/$PROJECT_NAME -n $PROJECT_NAME

echo "Verifying resources..."
kubectl get pods,svc,ingress -n $PROJECT_NAME

echo "Deployment complete. Application URL: http://cargo-tracker.example.com"
echo "To rollback: kubectl rollout undo deployment/$PROJECT_NAME -n $PROJECT_NAME"
