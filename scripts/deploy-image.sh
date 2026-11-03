#!/bin/bash
set -e
set -o pipefail

# Deploy cargo-tracker image to AWS EKS
# Usage: ./scripts/deploy-image.sh

PROJECT_NAME="cargo-tracker"

echo "=== Cargo Tracker Deployment to AWS EKS ==="

# Ask for AWS details
read -p "Enter AWS region (e.g., us-east-1): " AWS_REGION
read -p "Enter EKS cluster name: " CLUSTER_NAME
read -p "Enter full image URI (e.g., 123456789.dkr.ecr.us-east-1.amazonaws.com/cargo-tracker:latest): " IMAGE_URI

echo ""
echo "=== Configuring kubectl for EKS ==="
aws eks update-kubeconfig --region "$AWS_REGION" --name "$CLUSTER_NAME"
if [ $? -ne 0 ]; then
    echo "ERROR: Failed to configure kubectl"
    exit 1
fi

echo ""
echo "=== Checking cluster connectivity ==="
kubectl cluster-info || exit 1

# Ask for environment variables
echo ""
echo "=== Enter environment variable values ==="
echo "(Press Enter to skip a variable, which will leave its placeholder in place)"

read -p "Enter value for DB_JDBC_URL (or press Enter to skip): " DB_JDBC_URL
read -p "Enter value for DB_USER (or press Enter to skip): " DB_USER
read -p "Enter value for GRAPH_TRAVERSAL_URL (or press Enter to skip): " GRAPH_TRAVERSAL_URL

# Ask for credentials (without echoing)
read -s -p "Enter value for DB_PASSWORD (or press Enter to skip): " DB_PASSWORD
echo ""

echo ""
echo "=== Creating Kubernetes Secret ==="
SECRET_ARGS=""
if [ -n "$DB_PASSWORD" ]; then
    SECRET_ARGS="--from-literal=db-password=\"$DB_PASSWORD\""
fi

if [ -n "$SECRET_ARGS" ]; then
    kubectl create secret generic cargo-tracker-secrets -n cargo-tracker \
        $SECRET_ARGS \
        --dry-run=client -o yaml | kubectl apply -f -
    if [ $? -ne 0 ]; then
        echo "ERROR: Failed to create secret"
        exit 1
    fi
    echo "Secret created successfully."
else
    echo "No credentials provided, skipping secret creation."
fi

echo ""
echo "=== Applying Kubernetes manifests ==="

# Apply namespace
kubectl apply -f kubernetes/namespace.yaml
if [ $? -ne 0 ]; then
    echo "ERROR: Failed to apply namespace"
    exit 1
fi

# Apply deployment with placeholder substitution
sed -i.bak 's|{{IMAGE_URI}}|'$IMAGE_URI'|g' kubernetes/deployment.yaml
sed -i.bak 's|{{DB_JDBC_URL}}|'$DB_JDBC_URL'|g' kubernetes/deployment.yaml
sed -i.bak 's|{{DB_USER}}|'$DB_USER'|g' kubernetes/deployment.yaml
sed -i.bak 's|{{GRAPH_TRAVERSAL_URL}}|'$GRAPH_TRAVERSAL_URL'|g' kubernetes/deployment.yaml
rm -f kubernetes/deployment.yaml.bak

kubectl apply -f kubernetes/deployment.yaml
if [ $? -ne 0 ]; then
    echo "ERROR: Failed to apply deployment"
    exit 1
fi

# Apply service
kubectl apply -f kubernetes/service.yaml
if [ $? -ne 0 ]; then
    echo "ERROR: Failed to apply service"
    exit 1
fi

# Apply ingress
kubectl apply -f kubernetes/ingress.yaml
if [ $? -ne 0 ]; then
    echo "ERROR: Failed to apply ingress"
    exit 1
fi

echo ""
echo "=== Waiting for deployment rollout ==="
kubectl rollout status deployment/cargo-tracker -n cargo-tracker
if [ $? -ne 0 ]; then
    echo "ERROR: Deployment rollout failed"
    echo "Rollback instructions:"
    echo "  kubectl rollout undo deployment/cargo-tracker -n cargo-tracker"
    exit 1
fi

echo ""
echo "=== Deployment Details ==="
kubectl get pods,svc,ingress -n cargo-tracker

echo ""
echo "=== Application URL ==="
echo "http://cargo-tracker.example.com"

echo ""
echo "=== Deployment Complete ==="
echo "If you need to rollback:"
echo "  kubectl rollout undo deployment/cargo-tracker -n cargo-tracker"
