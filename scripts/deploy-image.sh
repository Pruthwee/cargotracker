#!/bin/bash
set -e
set -o pipefail

# Deploy script for cargo-tracker on AWS EKS
# Usage: ./scripts/deploy-image.sh

PROJECT_NAME="cargotrackerMMono"
IMAGE_NAME=$(echo "$PROJECT_NAME" | tr '[:upper:]' '[:lower:]' | tr -cs 'a-z0-9' '-' | sed 's/^-*//;s/-*$//')
NAMESPACE="cargo-tracker"

echo "=== Cargo Tracker EKS Deployment ==="
echo "Project name: $PROJECT_NAME"
echo "Image name: $IMAGE_NAME"
echo "Namespace: $NAMESPACE"
echo ""

# Ask for AWS configuration
read -p "Enter AWS region (e.g., us-east-1): " AWS_REGION
read -p "Enter EKS cluster name: " CLUSTER_NAME
read -p "Enter full image URI (registry/repo:tag): " IMAGE_URI

echo ""
echo "=== Configuration ==="
echo "AWS Region: $AWS_REGION"
echo "EKS Cluster: $CLUSTER_NAME"
echo "Image URI: $IMAGE_URI"
echo "Namespace: $NAMESPACE"
echo ""

# Ask for environment variables
echo "=== Environment Variables ==="
echo "Enter values for the application's environment variables."
echo "Press Enter to skip (use placeholder)."
echo ""

read -p "Enter value for DB_URL (or press Enter to skip): " DB_URL_VALUE
read -p "Enter value for GRAPH_TRAVERSAL_URL (or press Enter to skip): " GRAPH_TRAVERSAL_URL_VALUE

echo ""
echo "=== Credentials ==="
echo "Enter values for credentials (input will not be echoed)."
echo ""

read -s -p "Enter DB_USER: " DB_USER_VALUE
echo ""
read -s -p "Enter DB_PASSWORD: " DB_PASSWORD_VALUE
echo ""

echo ""
echo "=== Configuring kubectl for EKS ==="
aws eks update-kubeconfig --region "$AWS_REGION" --name "$CLUSTER_NAME"
kubectl cluster-info || exit 1

echo ""
echo "=== Creating Kubernetes Secret ==="
kubectl create secret generic cargo-tracker-secrets -n "$NAMESPACE" \
  --from-literal=db-user="$DB_USER_VALUE" \
  --from-literal=db-password="$DB_PASSWORD_VALUE" \
  --dry-run=client -o yaml | kubectl apply -f -

echo ""
echo "=== Applying Kubernetes manifests ==="
kubectl apply -f kubernetes/namespace.yaml
kubectl apply -f kubernetes/deployment.yaml
kubectl apply -f kubernetes/service.yaml
kubectl apply -f kubernetes/ingress.yaml

echo ""
echo "=== Replacing placeholders in deployment.yaml ==="
# Create a temporary copy with placeholders replaced
cp kubernetes/deployment.yaml /tmp/deployment.yaml.bak

sed -i.bak 's|{{IMAGE_URI}}|'"$IMAGE_URI"'|g' /tmp/deployment.yaml.bak
sed -i.bak 's|{{DB_URL}}|'"$DB_URL_VALUE"'|g' /tmp/deployment.yaml.bak
sed -i.bak 's|{{GRAPH_TRAVERSAL_URL}}|'"$GRAPH_TRAVERSAL_URL_VALUE"'|g' /tmp/deployment.yaml.bak
rm -f /tmp/deployment.yaml.bak.bak

kubectl apply -f /tmp/deployment.yaml.bak
rm -f /tmp/deployment.yaml.bak

echo ""
echo "=== Waiting for deployment rollout ==="
kubectl rollout status deployment/cargo-tracker -n "$NAMESPACE"

echo ""
echo "=== Deployment status ==="
kubectl get pods,svc,ingress -n "$NAMESPACE"

echo ""
echo "=== Application URL ==="
kubectl get ingress cargo-tracker-ingress -n "$NAMESPACE" -o jsonpath='{.status.loadBalancer.ingress[0].hostname}'

echo ""
echo "=== Deployment complete ==="
echo ""
echo "Rollback instructions:"
echo "  kubectl rollout undo deployment/cargo-tracker -n $NAMESPACE"
echo ""
echo "To troubleshoot:"
echo "  kubectl describe pods -n $NAMESPACE"
echo "  kubectl logs -l app=cargo-tracker -n $NAMESPACE"
