#!/bin/bash
set -e
set -o pipefail

echo "============================================"
echo "  Cargo Tracker - Deploy to AWS EKS"
echo "============================================"
echo ""

# Prompt for AWS region
read -rp "Enter AWS Region (e.g. us-east-1): " AWS_REGION
if [ -z "$AWS_REGION" ]; then
  echo "ERROR: AWS Region is required." >&2
  exit 1
fi

# Prompt for EKS cluster name
read -rp "Enter EKS Cluster Name: " CLUSTER_NAME
if [ -z "$CLUSTER_NAME" ]; then
  echo "ERROR: EKS Cluster Name is required." >&2
  exit 1
fi

# Prompt for Docker image URI
read -rp "Enter full Docker image URI (e.g. 123456789.dkr.ecr.us-east-1.amazonaws.com/cargo-tracker:latest): " IMAGE_URI
if [ -z "$IMAGE_URI" ]; then
  echo "ERROR: Docker image URI is required." >&2
  exit 1
fi

echo ""
echo "--- Application Configuration ---"
echo "The following environment variables are used by cargo-tracker."
echo "Press Enter to keep the placeholder (you can update them in the Kubernetes manifest later)."
echo ""

read -rp "Enter DB_JDBC_URL (PostgreSQL JDBC URL, e.g. jdbc:postgresql://host:5432/postgres): " DB_JDBC_URL
if [ -z "$DB_JDBC_URL" ]; then
  DB_JDBC_URL="jdbc:postgresql://localhost:5432/postgres"
fi

read -rp "Enter DB_USER (PostgreSQL username): " DB_USER
if [ -z "$DB_USER" ]; then
  DB_USER="postgres"
fi

read -rsp "Enter DB_PASSWORD (PostgreSQL password): " DB_PASSWORD
echo ""
if [ -z "$DB_PASSWORD" ]; then
  DB_PASSWORD="postgres"
fi

read -rp "Enter REDIS_HOST (Redis/ElastiCache host): " REDIS_HOST
if [ -z "$REDIS_HOST" ]; then
  REDIS_HOST="localhost"
fi

read -rp "Enter REDIS_PORT (Redis port, default 6379): " REDIS_PORT
if [ -z "$REDIS_PORT" ]; then
  REDIS_PORT="6379"
fi

read -rp "Enter GRAPH_TRAVERSAL_URL (default: http://localhost:8080/rest/graph-traversal/shortest-path): " GRAPH_TRAVERSAL_URL
if [ -z "$GRAPH_TRAVERSAL_URL" ]; then
  GRAPH_TRAVERSAL_URL="http://localhost:8080/rest/graph-traversal/shortest-path"
fi

echo ""
echo "--- Configuring kubectl for EKS ---"
aws eks update-kubeconfig --region "$AWS_REGION" --name "$CLUSTER_NAME"
if [ $? -ne 0 ]; then
  echo "ERROR: Failed to configure kubectl for EKS cluster '$CLUSTER_NAME'." >&2
  exit 1
fi

echo ""
echo "--- Verifying cluster connectivity ---"
kubectl cluster-info || { echo "ERROR: Cannot connect to EKS cluster." >&2; exit 1; }

echo ""
echo "--- Updating Kubernetes manifests ---"

# Work on copies to avoid modifying originals permanently
cp kubernetes/deployment.yaml /tmp/cargo-tracker-deployment.yaml
cp kubernetes/service.yaml    /tmp/cargo-tracker-service.yaml
cp kubernetes/ingress.yaml    /tmp/cargo-tracker-ingress.yaml
cp kubernetes/namespace.yaml  /tmp/cargo-tracker-namespace.yaml

# Replace all placeholders using pipe delimiter
sed -i 's|{{IMAGE_URI}}|'"$IMAGE_URI"'|g'              /tmp/cargo-tracker-deployment.yaml
sed -i 's|{{DB_JDBC_URL}}|'"$DB_JDBC_URL"'|g'          /tmp/cargo-tracker-deployment.yaml
sed -i 's|{{DB_USER}}|'"$DB_USER"'|g'                  /tmp/cargo-tracker-deployment.yaml
sed -i 's|{{DB_PASSWORD}}|'"$DB_PASSWORD"'|g'           /tmp/cargo-tracker-deployment.yaml
sed -i 's|{{REDIS_HOST}}|'"$REDIS_HOST"'|g'             /tmp/cargo-tracker-deployment.yaml
sed -i 's|{{REDIS_PORT}}|'"$REDIS_PORT"'|g'             /tmp/cargo-tracker-deployment.yaml
sed -i 's|{{GRAPH_TRAVERSAL_URL}}|'"$GRAPH_TRAVERSAL_URL"'|g' /tmp/cargo-tracker-deployment.yaml

echo ""
echo "--- Applying Kubernetes manifests ---"

echo "Applying namespace..."
kubectl apply -f /tmp/cargo-tracker-namespace.yaml

echo "Applying deployment..."
kubectl apply -f /tmp/cargo-tracker-deployment.yaml

echo "Applying service..."
kubectl apply -f /tmp/cargo-tracker-service.yaml

echo "Applying ingress..."
kubectl apply -f /tmp/cargo-tracker-ingress.yaml

echo ""
echo "--- Waiting for deployment rollout ---"
kubectl rollout status deployment/cargo-tracker -n cargo-tracker --timeout=300s
if [ $? -ne 0 ]; then
  echo ""
  echo "ERROR: Deployment rollout failed. Rolling back..." >&2
  kubectl rollout undo deployment/cargo-tracker -n cargo-tracker
  echo "Rollback initiated. Check pod logs with:"
  echo "  kubectl logs -l app=cargo-tracker -n cargo-tracker"
  exit 1
fi

echo ""
echo "--- Verifying deployed resources ---"
kubectl get pods,svc,ingress -n cargo-tracker

echo ""
echo "--- Application Access ---"
INGRESS_HOST=$(kubectl get ingress cargo-tracker-ingress -n cargo-tracker \
  -o jsonpath='{.status.loadBalancer.ingress[0].hostname}' 2>/dev/null || echo "pending")
if [ "$INGRESS_HOST" != "pending" ] && [ -n "$INGRESS_HOST" ]; then
  echo "Application URL: http://${INGRESS_HOST}"
else
  echo "Ingress hostname is still provisioning. Run the following to check:"
  echo "  kubectl get ingress cargo-tracker-ingress -n cargo-tracker"
fi

echo ""
echo "============================================"
echo "  SUCCESS: cargo-tracker deployed to EKS!"
echo "============================================"
echo ""
echo "Useful commands:"
echo "  kubectl get pods -n cargo-tracker"
echo "  kubectl logs -l app=cargo-tracker -n cargo-tracker"
echo "  kubectl describe deployment cargo-tracker -n cargo-tracker"
echo "  kubectl rollout undo deployment/cargo-tracker -n cargo-tracker  # rollback"
