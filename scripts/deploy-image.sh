#!/bin/bash
set -e
set -o pipefail

echo "=== Cargo Tracker EKS Deployment Script ==="
echo ""

# Project name
PROJECT_NAME="cargo-tracker"
IMAGE_NAME=$(echo "$PROJECT_NAME" | tr '[:upper:]' '[:lower:]' | tr -cs 'a-z0-9' '-' | sed 's/^-*//;s/-*$//')

# Ask for AWS details
read -p "Enter AWS region (e.g., us-east-1): " AWS_REGION
read -p "Enter EKS cluster name: " CLUSTER_NAME
read -p "Enter image URI (full path with registry and tag, e.g., 123456789.dkr.ecr.us-east-1.amazonaws.com/cargo-tracker:latest): " IMAGE_URI

# Ask for environment variables
echo ""
echo "=== Environment Variables ==="
echo "Enter values for the application's environment variables."
echo "Press Enter to skip (use the placeholder default)."
echo ""

read -p "Enter value for GRAPH_TRAVERSAL_URL (or press Enter to skip): " GRAPH_TRAVERSAL_URL
if [ -z "$GRAPH_TRAVERSAL_URL" ]; then
  GRAPH_TRAVERSAL_URL="http://${IMAGE_NAME}:8080/rest/graph-traversal/shortest-path"
fi

# Ask for credentials
echo ""
echo "=== Credentials ==="
echo "These will be stored in a Kubernetes Secret."
echo ""

read -p "Enter DB URL (or press Enter to skip): " DB_URL
read -p "Enter DB user (or press Enter to skip): " DB_USER
read -s -p "Enter DB password (or press Enter to skip): " DB_PASSWORD
echo ""

# Configure kubectl for EKS
echo ""
echo "=== Configuring kubectl for EKS ==="
aws eks update-kubeconfig --region "$AWS_REGION" --name "$CLUSTER_NAME"
echo "kubectl configured for cluster: $CLUSTER_NAME"

# Verify cluster connectivity
echo ""
echo "=== Verifying cluster connectivity ==="
kubectl cluster-info || exit 1
echo "Cluster connectivity verified."

# Apply namespace
echo ""
echo "=== Applying namespace ==="
kubectl apply -f kubernetes/namespace.yaml
echo "Namespace applied."

# Create Secret with credentials
echo ""
echo "=== Creating Kubernetes Secret ==="
SECRET_ARGS=""
if [ -n "$DB_URL" ]; then
  SECRET_ARGS="$SECRET_ARGS --from-literal=db-url=$DB_URL"
fi
if [ -n "$DB_USER" ]; then
  SECRET_ARGS="$SECRET_ARGS --from-literal=db-user=$DB_USER"
fi
if [ -n "$DB_PASSWORD" ]; then
  SECRET_ARGS="$SECRET_ARGS --from-literal=db-password=$DB_PASSWORD"
fi

if [ -n "$SECRET_ARGS" ]; then
  kubectl create secret generic ${IMAGE_NAME}-secrets -n ${IMAGE_NAME} $SECRET_ARGS --dry-run=client -o yaml | kubectl apply -f -
  echo "Secret created."
else
  echo "No credentials provided, skipping Secret creation."
fi

# Replace placeholders in deployment.yaml
echo ""
echo "=== Deploying application ==="
sed -i.bak 's|{{IMAGE_URI}}|'$IMAGE_URI'|g' kubernetes/deployment.yaml
sed -i.bak 's|{{NAMESPACE}}|'$IMAGE_NAME'|g' kubernetes/deployment.yaml
sed -i.bak 's|{{GRAPH_TRAVERSAL_URL}}|'$GRAPH_TRAVERSAL_URL'|g' kubernetes/deployment.yaml
rm -f kubernetes/deployment.yaml.bak

# Apply deployment
kubectl apply -f kubernetes/deployment.yaml
echo "Deployment applied."

# Apply service
kubectl apply -f kubernetes/service.yaml
echo "Service applied."

# Apply ingress
kubectl apply -f kubernetes/ingress.yaml
echo "Ingress applied."

# Wait for rollout
echo ""
echo "=== Waiting for deployment rollout ==="
kubectl rollout status deployment/${IMAGE_NAME} -n ${IMAGE_NAME}
echo "Deployment rollout complete."

# Show resources
echo ""
echo "=== Deployed Resources ==="
kubectl get pods,svc,ingress -n ${IMAGE_NAME}

# Show application URL
echo ""
echo "=== Application URL ==="
echo "http://${IMAGE_NAME}.example.com"

echo ""
echo "=== Deployment Complete ==="
echo "If you need to rollback, run:"
echo "  kubectl rollout undo deployment/${IMAGE_NAME} -n ${IMAGE_NAME}"
