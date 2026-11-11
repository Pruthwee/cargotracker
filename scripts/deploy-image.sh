#!/bin/bash
set -e
set -o pipefail

PROJECT_NAME="cargo-tracker"
IMAGE_NAME=$(echo "$PROJECT_NAME" | tr '[:upper:]' '[:lower:]' | tr -cs 'a-z0-9' '-' | sed 's/^-*//;s/-*$//')

echo "=== Cargo Tracker EKS Deployment ==="
echo "Project name: $PROJECT_NAME"
echo "Image name: $IMAGE_NAME"

echo ""
echo "=== AWS EKS Configuration ==="
read -p "Enter AWS region: " AWS_REGION
read -p "Enter EKS cluster name: " CLUSTER_NAME
read -p "Enter image URI (full path with registry and tag): " IMAGE_URI
read -p "Enter DB JDBC URL (or press Enter to skip): " DB_JDBC_URL
read -p "Enter DB user (or press Enter to skip): " DB_USER
read -p "Enter DB password: " DB_PASSWORD
read -p "Enter Graph Traversal URL (or press Enter to skip): " GRAPH_TRAVERSAL_URL

echo ""
echo "=== Configuring kubectl for EKS ==="
aws eks update-kubeconfig --region "$AWS_REGION" --name "$CLUSTER_NAME"
kubectl cluster-info || exit 1
echo "kubectl configured for cluster: $CLUSTER_NAME"

echo ""
echo "=== Creating Kubernetes Secret ==="
kubectl create secret generic cargo-tracker-secrets \
  -n cargo-tracker \
  --from-literal=db_password="$DB_PASSWORD" \
  --dry-run=client -o yaml | kubectl apply -f -
echo "Secret created: cargo-tracker-secrets"

echo ""
echo "=== Applying namespace ==="
kubectl apply -f kubernetes/namespace.yaml

echo ""
echo "=== Replacing placeholders in deployment.yaml ==="
sed -i.bak 's|{{IMAGE_URI}}|'$IMAGE_URI'|g' kubernetes/deployment.yaml
sed -i.bak 's|{{DB_JDBC_URL}}|'$DB_JDBC_URL'|g' kubernetes/deployment.yaml
sed -i.bak 's|{{DB_USER}}|'$DB_USER'|g' kubernetes/deployment.yaml
sed -i.bak 's|{{GRAPH_TRAVERSAL_URL}}|'$GRAPH_TRAVERSAL_URL'|g' kubernetes/deployment.yaml
rm -f kubernetes/deployment.yaml.bak

echo ""
echo "=== Applying deployment, service, and ingress ==="
kubectl apply -f kubernetes/deployment.yaml
kubectl apply -f kubernetes/service.yaml
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
echo "If the deployment fails, rollback with:"
echo "  kubectl rollout undo deployment/cargo-tracker -n cargo-tracker"
