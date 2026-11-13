#!/bin/bash
set -e
set -o pipefail

# Deploy image to AWS EKS

echo "=== Cargo Tracker Deploy to AWS EKS ==="

PROJECT_NAME="Cargo-MONO-1"
IMAGE_NAME=$(echo "$PROJECT_NAME" | tr '[:upper:]' '[:lower:]' | tr -cs 'a-z0-9' '-' | sed 's/^-*//;s/-*$//')

# Ask for AWS region
read -p "Enter AWS region (e.g., us-east-1): " AWS_REGION
if [ -z "$AWS_REGION" ]; then
  echo "AWS region is required."
  exit 1
fi

# Ask for EKS cluster name
read -p "Enter EKS cluster name: " CLUSTER_NAME
if [ -z "$CLUSTER_NAME" ]; then
  echo "EKS cluster name is required."
  exit 1
fi

# Ask for image URI
read -p "Enter full image URI (e.g., 123456789.dkr.ecr.us-east-1.amazonaws.com/cargo-mono-1:latest): " IMAGE_URI
if [ -z "$IMAGE_URI" ]; then
  echo "Image URI is required."
  exit 1
fi

# Ask for environment variables (credentials prompted without echo)
read -p "Enter value for DB_JDBC_URL (or press Enter to skip): " DB_JDBC_URL
read -p "Enter value for GRAPH_TRAVERSAL_URL (or press Enter to skip): " GRAPH_TRAVERSAL_URL
read -s -p "Enter value for DB_USER (credential, not echoed): " DB_USER
echo ""
read -s -p "Enter value for DB_PASSWORD (credential, not echoed): " DB_PASSWORD
echo ""

# Configure kubectl for EKS
echo "Configuring kubectl for EKS cluster: $CLUSTER_NAME in region $AWS_REGION"
aws eks update-kubeconfig --region "$AWS_REGION" --name "$CLUSTER_NAME"

# Verify cluster connectivity
kubectl cluster-info || exit 1

# Create namespace
echo "Creating namespace..."
kubectl apply -f kubernetes/namespace.yaml

# Create Secret for credentials
echo "Creating Kubernetes Secret for credentials..."
kubectl create secret generic cargo-tracker-secrets \
  -n cargo-tracker \
  --from-literal=db-user="$DB_USER" \
  --from-literal=db-password="$DB_PASSWORD" \
  --dry-run=client -o yaml | kubectl apply -f -

# Replace placeholders in deployment.yaml and other manifests
echo "Replacing placeholders in deployment manifests..."
sed -i.bak 's|{{IMAGE_URI}}|'"$IMAGE_URI"'|g' kubernetes/deployment.yaml
sed -i.bak 's|{{NAMESPACE}}|cargo-tracker|g' kubernetes/deployment.yaml
sed -i.bak 's|{{DB_JDBC_URL}}|'"$DB_JDBC_URL"'|g' kubernetes/deployment.yaml
sed -i.bak 's|{{GRAPH_TRAVERSAL_URL}}|'"$GRAPH_TRAVERSAL_URL"'|g' kubernetes/deployment.yaml
rm -f kubernetes/deployment.yaml.bak

sed -i.bak 's|{{NAMESPACE}}|cargo-tracker|g' kubernetes/service.yaml
rm -f kubernetes/service.yaml.bak

sed -i.bak 's|{{NAMESPACE}}|cargo-tracker|g' kubernetes/ingress.yaml
rm -f kubernetes/ingress.yaml.bak

# Apply deployment
echo "Applying deployment..."
kubectl apply -f kubernetes/deployment.yaml

# Apply service
echo "Applying service..."
kubectl apply -f kubernetes/service.yaml

# Apply ingress
echo "Applying ingress..."
kubectl apply -f kubernetes/ingress.yaml

# Wait for rollout
echo "Waiting for deployment rollout..."
kubectl rollout status deployment/cargo-tracker -n cargo-tracker

# Show resources
echo ""
echo "=== Deployed resources ==="
kubectl get pods,svc,ingress -n cargo-tracker

# Show application URL
echo ""
echo "=== Application URL ==="
echo "https://cargo-tracker.example.com"

echo ""
echo "=== Deployment complete ==="
