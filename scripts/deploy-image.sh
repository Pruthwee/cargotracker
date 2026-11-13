#!/bin/bash
set -e
set -o pipefail

echo "=== Cargo Tracker - Deploy to AWS EKS ==="

# Project name
PROJECT_NAME="cargo-tracker"
IMAGE_NAME="cargo-tracker"

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
read -p "Enter full image URI (registry/repo:tag): " IMAGE_URI
if [ -z "$IMAGE_URI" ]; then
  echo "Image URI is required."
  exit 1
fi

# Ask for environment variables
echo ""
echo "Enter values for environment variables (press Enter to skip and use placeholder):"
echo ""

read -p "Enter value for DB_JDBC_URL (or press Enter to skip): " DB_JDBC_URL_VAL
read -p "Enter value for DB_USER (or press Enter to skip): " DB_USER_VAL
read -s -p "Enter value for DB_PASSWORD (hidden): " DB_PASSWORD_VAL
echo ""
read -p "Enter value for GRAPH_TRAVERSAL_URL (or press Enter to skip): " GRAPH_TRAVERSAL_URL_VAL

echo ""
echo "=== Configuring kubectl ==="
aws eks update-kubeconfig --region "$AWS_REGION" --name "$CLUSTER_NAME"

echo ""
echo "=== Checking cluster connectivity ==="
kubectl cluster-info || exit 1

echo ""
echo "=== Creating namespace ==="
kubectl apply -f kubernetes/namespace.yaml

echo ""
echo "=== Creating secrets ==="
if [ -n "$DB_USER_VAL" ] && [ -n "$DB_PASSWORD_VAL" ]; then
  kubectl create secret generic cargo-tracker-secrets -n cargo-tracker \
    --from-literal=db-user="$DB_USER_VAL" \
    --from-literal=db-password="$DB_PASSWORD_VAL" \
    --dry-run=client -o yaml | kubectl apply -f -
else
  echo "Skipping secret creation (DB_USER or DB_PASSWORD not provided)"
fi

echo ""
echo "=== Deploying application ==="
# Replace placeholders in deployment.yaml using sed with pipe delimiter
sed -i.bak 's|{{IMAGE_URI}}|'"$IMAGE_URI"'|g' kubernetes/deployment.yaml
if [ -n "$DB_JDBC_URL_VAL" ]; then
  sed -i.bak 's|{{DB_JDBC_URL}}|'"$DB_JDBC_URL_VAL"'|g' kubernetes/deployment.yaml
fi
if [ -n "$DB_USER_VAL" ]; then
  sed -i.bak 's|{{DB_USER}}|'"$DB_USER_VAL"'|g' kubernetes/deployment.yaml
fi
if [ -n "$GRAPH_TRAVERSAL_URL_VAL" ]; then
  sed -i.bak 's|{{GRAPH_TRAVERSAL_URL}}|'"$GRAPH_TRAVERSAL_URL_VAL"'|g' kubernetes/deployment.yaml
fi
rm -f kubernetes/deployment.yaml.bak

kubectl apply -f kubernetes/deployment.yaml

echo ""
echo "=== Creating service ==="
kubectl apply -f kubernetes/service.yaml

echo ""
echo "=== Creating ingress ==="
kubectl apply -f kubernetes/ingress.yaml

echo ""
echo "=== Waiting for deployment rollout ==="
kubectl rollout status deployment/cargo-tracker -n cargo-tracker

echo ""
echo "=== Deployment status ==="
kubectl get pods,svc,ingress -n cargo-tracker

echo ""
echo "=== Application URL ==="
kubectl get ingress cargo-tracker-ingress -n cargo-tracker -o jsonpath='{.status.loadBalancer.ingress[0].hostname}'

echo ""
echo "=== Deployment complete ==="
echo "If the deployment fails, roll back with:"
echo "  kubectl rollout undo deployment/cargo-tracker -n cargo-tracker"
