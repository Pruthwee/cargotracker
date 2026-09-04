#!/bin/bash
set -e
set -o pipefail

echo "============================================"
echo "  Cargo Tracker - Deploy to AWS EKS"
echo "============================================"
echo ""

# Prompt for AWS configuration
read -p "Enter AWS Region (e.g. us-east-1): " AWS_REGION
if [ -z "$AWS_REGION" ]; then
  echo "ERROR: AWS Region is required."
  exit 1
fi

read -p "Enter EKS Cluster Name: " CLUSTER_NAME
if [ -z "$CLUSTER_NAME" ]; then
  echo "ERROR: EKS Cluster Name is required."
  exit 1
fi

read -p "Enter full Docker image URI (e.g. 123456789.dkr.ecr.us-east-1.amazonaws.com/cargo-tracker:latest): " IMAGE_URI
if [ -z "$IMAGE_URI" ]; then
  echo "ERROR: Docker image URI is required."
  exit 1
fi

echo ""
echo "--- Application Configuration ---"
echo "Press Enter to skip any optional value (placeholder will remain in manifest)."
echo ""

read -p "Enter PostgreSQL JDBC URL (e.g. jdbc:postgresql://host:5432/cargotracker): " POSTGRES_JDBC_URL
read -p "Enter PostgreSQL Username: " POSTGRES_USERNAME
read -sp "Enter PostgreSQL Password: " POSTGRES_PASSWORD
echo ""
read -p "Enter Graph Traversal URL (default: http://localhost:8080/rest/graph-traversal/shortest-path): " GRAPH_TRAVERSAL_URL_INPUT
GRAPH_TRAVERSAL_URL="${GRAPH_TRAVERSAL_URL_INPUT:-http://localhost:8080/rest/graph-traversal/shortest-path}"
read -p "Enter Redis Host (e.g. my-redis.cache.amazonaws.com): " REDIS_HOST
read -p "Enter Redis Port (default: 6379): " REDIS_PORT_INPUT
REDIS_PORT="${REDIS_PORT_INPUT:-6379}"

echo ""
echo "--- Configuring kubectl for EKS ---"
aws eks update-kubeconfig --region "$AWS_REGION" --name "$CLUSTER_NAME"

echo "Verifying cluster connectivity..."
kubectl cluster-info || { echo "ERROR: Cannot connect to EKS cluster. Check your credentials and cluster name."; exit 1; }

echo ""
echo "--- Updating Kubernetes manifests ---"

# Update deployment.yaml with actual values using pipe delimiter
sed -i "s|{{IMAGE_URI}}|${IMAGE_URI}|g" kubernetes/deployment.yaml

if [ -n "$POSTGRES_JDBC_URL" ]; then
  sed -i "s|{{POSTGRES_JDBC_URL}}|${POSTGRES_JDBC_URL}|g" kubernetes/deployment.yaml
fi
if [ -n "$POSTGRES_USERNAME" ]; then
  sed -i "s|{{POSTGRES_USERNAME}}|${POSTGRES_USERNAME}|g" kubernetes/deployment.yaml
fi
if [ -n "$POSTGRES_PASSWORD" ]; then
  sed -i "s|{{POSTGRES_PASSWORD}}|${POSTGRES_PASSWORD}|g" kubernetes/deployment.yaml
fi
sed -i "s|{{GRAPH_TRAVERSAL_URL}}|${GRAPH_TRAVERSAL_URL}|g" kubernetes/deployment.yaml
if [ -n "$REDIS_HOST" ]; then
  sed -i "s|{{REDIS_HOST}}|${REDIS_HOST}|g" kubernetes/deployment.yaml
fi
sed -i "s|{{REDIS_PORT}}|${REDIS_PORT}|g" kubernetes/deployment.yaml

echo ""
echo "--- Applying Kubernetes manifests ---"

echo "Applying namespace..."
kubectl apply -f kubernetes/namespace.yaml

echo "Applying deployment..."
kubectl apply -f kubernetes/deployment.yaml

echo "Applying service..."
kubectl apply -f kubernetes/service.yaml

echo "Applying ingress..."
kubectl apply -f kubernetes/ingress.yaml

echo ""
echo "--- Waiting for deployment rollout ---"
kubectl rollout status deployment/cargo-tracker -n cargo-tracker --timeout=300s

echo ""
echo "--- Verifying deployed resources ---"
kubectl get pods,svc,ingress -n cargo-tracker

echo ""
echo "--- Application Access ---"
INGRESS_HOST=$(kubectl get ingress cargo-tracker-ingress -n cargo-tracker -o jsonpath='{.status.loadBalancer.ingress[0].hostname}' 2>/dev/null || echo "pending")
if [ "$INGRESS_HOST" != "pending" ] && [ -n "$INGRESS_HOST" ]; then
  echo "Application URL: http://${INGRESS_HOST}"
else
  echo "Ingress hostname is still being provisioned. Run the following to check:"
  echo "  kubectl get ingress cargo-tracker-ingress -n cargo-tracker"
fi

echo ""
echo "============================================"
echo "  Deployment Complete!"
echo "============================================"
echo ""
echo "Rollback command (if needed):"
echo "  kubectl rollout undo deployment/cargo-tracker -n cargo-tracker"
