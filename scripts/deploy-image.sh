#!/bin/bash
set -e
set -o pipefail

PROJECT_NAME="cargotracker"

echo "--- AWS EKS Deployment Script ---"

read -p "Enter AWS Region [us-east-1]: " AWS_REGION
AWS_REGION=${AWS_REGION:-us-east-1}

read -p "Enter EKS Cluster Name: " CLUSTER_NAME
if [ -z "$CLUSTER_NAME" ]; then echo "Cluster name is required"; exit 1; fi

read -p "Enter Docker Image URI (e.g., account.dkr.ecr.region.amazonaws.com/repo:tag): " IMAGE_URI
if [ -z "$IMAGE_URI" ]; then echo "Image URI is required"; exit 1; fi

# Prompt for environment variables
echo "Enter application configuration (press Enter to skip):"
read -p "DB_JDBC_URL: " DB_JDBC_URL
read -p "DB_USER: " DB_USER
read -p "DB_PASSWORD: " DB_PASSWORD

# Update manifests
sed -i "s|{{IMAGE_URI}}|$IMAGE_URI|g" kubernetes/deployment.yaml
sed -i "s|{{DB_JDBC_URL}}|$DB_JDBC_URL|g" kubernetes/deployment.yaml
sed -i "s|{{DB_USER}}|$DB_USER|g" kubernetes/deployment.yaml
sed -i "s|{{DB_PASSWORD}}|$DB_PASSWORD|g" kubernetes/deployment.yaml

echo "Configuring kubectl for EKS..."
aws eks update-kubeconfig --region $AWS_REGION --name $CLUSTER_NAME

echo "Verifying cluster connectivity..."
kubectl cluster-info || { echo "Cluster connectivity failed"; exit 1; }

echo "Applying Kubernetes manifests..."
kubectl apply -f kubernetes/namespace.yaml
kubectl apply -f kubernetes/deployment.yaml
kubectl apply -f kubernetes/service.yaml
kubectl apply -f kubernetes/ingress.yaml

echo "Waiting for rollout..."
kubectl rollout status deployment/$PROJECT_NAME -n $PROJECT_NAME

echo "Verifying resources..."
kubectl get pods,svc,ingress -n $PROJECT_NAME

echo "Deployment complete!"
echo "Application URL: http://cargotracker.example.com"
echo "If deployment failed, use: kubectl rollout undo deployment/$PROJECT_NAME -n $PROJECT_NAME"
