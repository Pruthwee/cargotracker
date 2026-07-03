#!/bin/bash
set -e
set -o pipefail

# ============================================================
# deploy-image.sh - Deploy cargo-tracker to AWS EKS
# ============================================================

APP_NAME="cargo-tracker"
NAMESPACE="cargo-tracker"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

echo "============================================"
echo "  cargo-tracker - Deploy to AWS EKS"
echo "============================================"
echo ""

# Prompt for AWS region
read -p "Enter AWS Region (e.g., us-east-1): " AWS_REGION
if [ -z "$AWS_REGION" ]; then
  echo "ERROR: AWS Region is required."
  exit 1
fi

# Prompt for EKS cluster name
read -p "Enter EKS Cluster Name: " CLUSTER_NAME
if [ -z "$CLUSTER_NAME" ]; then
  echo "ERROR: EKS Cluster Name is required."
  exit 1
fi

# Prompt for Docker image URI
read -p "Enter full Docker image URI (e.g., 123456789.dkr.ecr.us-east-1.amazonaws.com/cargo-tracker:latest): " IMAGE_URI
if [ -z "$IMAGE_URI" ]; then
  echo "ERROR: Docker image URI is required."
  exit 1
fi

echo ""
echo "--- Optional: Application Configuration ---"
echo "Press Enter to skip any value (placeholder will remain in manifest)"
echo ""

read -p "Enter DB_JDBC_URL (e.g., jdbc:postgresql://host:5432/cargotracker): " DB_JDBC_URL
read -p "Enter DB_DRIVER_CLASS (e.g., org.postgresql.ds.PGPoolingDataSource): " DB_DRIVER_CLASS
read -p "Enter DB_USER: " DB_USER
read -s -p "Enter DB_PASSWORD: " DB_PASSWORD
echo ""
read -p "Enter GRAPH_TRAVERSAL_URL (e.g., http://localhost:8080/cargo-tracker/rest/graph-traversal/shortest-path): " GRAPH_TRAVERSAL_URL

echo ""
echo "--------------------------------------------"
echo "Configuring kubectl for EKS cluster: $CLUSTER_NAME"
aws eks update-kubeconfig --region "$AWS_REGION" --name "$CLUSTER_NAME"
if [ $? -ne 0 ]; then
  echo "ERROR: Failed to configure kubectl for EKS cluster."
  exit 1
fi

echo "Verifying cluster connectivity..."
kubectl cluster-info || { echo "ERROR: Cannot connect to Kubernetes cluster."; exit 1; }

echo ""
echo "--------------------------------------------"
echo "Updating Kubernetes manifests..."

# Copy manifests to a temp working directory
TEMP_DIR=$(mktemp -d)
cp -r "$PROJECT_ROOT/kubernetes/"* "$TEMP_DIR/"

# Replace IMAGE_URI placeholder
sed -i "s|{{IMAGE_URI}}|${IMAGE_URI}|g" "$TEMP_DIR/deployment.yaml"

# Replace optional environment variable placeholders
if [ -n "$DB_JDBC_URL" ]; then
  sed -i "s|{{DB_JDBC_URL}}|${DB_JDBC_URL}|g" "$TEMP_DIR/deployment.yaml"
else
  sed -i "s|{{DB_JDBC_URL}}|jdbc:h2:file:./cargo-tracker-data/cargo-tracker-database|g" "$TEMP_DIR/deployment.yaml"
fi

if [ -n "$DB_DRIVER_CLASS" ]; then
  sed -i "s|{{DB_DRIVER_CLASS}}|${DB_DRIVER_CLASS}|g" "$TEMP_DIR/deployment.yaml"
else
  sed -i "s|{{DB_DRIVER_CLASS}}|org.h2.jdbcx.JdbcDataSource|g" "$TEMP_DIR/deployment.yaml"
fi

if [ -n "$DB_USER" ]; then
  sed -i "s|{{DB_USER}}|${DB_USER}|g" "$TEMP_DIR/deployment.yaml"
else
  sed -i "s|{{DB_USER}}||g" "$TEMP_DIR/deployment.yaml"
fi

if [ -n "$DB_PASSWORD" ]; then
  sed -i "s|{{DB_PASSWORD}}|${DB_PASSWORD}|g" "$TEMP_DIR/deployment.yaml"
else
  sed -i "s|{{DB_PASSWORD}}||g" "$TEMP_DIR/deployment.yaml"
fi

if [ -n "$GRAPH_TRAVERSAL_URL" ]; then
  sed -i "s|{{GRAPH_TRAVERSAL_URL}}|${GRAPH_TRAVERSAL_URL}|g" "$TEMP_DIR/deployment.yaml"
else
  sed -i "s|{{GRAPH_TRAVERSAL_URL}}|http://localhost:8080/cargo-tracker/rest/graph-traversal/shortest-path|g" "$TEMP_DIR/deployment.yaml"
fi

echo ""
echo "--------------------------------------------"
echo "Applying Kubernetes manifests..."

echo "1. Applying namespace..."
kubectl apply -f "$TEMP_DIR/namespace.yaml"

echo "2. Applying deployment..."
kubectl apply -f "$TEMP_DIR/deployment.yaml"

echo "3. Applying service..."
kubectl apply -f "$TEMP_DIR/service.yaml"

echo "4. Applying ingress..."
kubectl apply -f "$TEMP_DIR/ingress.yaml"

echo ""
echo "--------------------------------------------"
echo "Waiting for deployment rollout..."
kubectl rollout status deployment/${APP_NAME} -n ${NAMESPACE} --timeout=300s
if [ $? -ne 0 ]; then
  echo "ERROR: Deployment rollout failed. Rolling back..."
  kubectl rollout undo deployment/${APP_NAME} -n ${NAMESPACE}
  echo "Rollback initiated. Check pod status with:"
  echo "  kubectl get pods -n ${NAMESPACE}"
  exit 1
fi

echo ""
echo "--------------------------------------------"
echo "Verifying deployed resources..."
kubectl get pods,svc,ingress -n ${NAMESPACE}

echo ""
echo "--------------------------------------------"
echo "Application Ingress URL:"
kubectl get ingress ${APP_NAME}-ingress -n ${NAMESPACE} -o jsonpath='{.status.loadBalancer.ingress[0].hostname}' 2>/dev/null || \
  echo "  (Ingress hostname not yet assigned - check with: kubectl get ingress -n ${NAMESPACE})"

echo ""
echo "============================================"
echo "  SUCCESS: cargo-tracker deployed to EKS!"
echo "  Namespace: ${NAMESPACE}"
echo "  Image: ${IMAGE_URI}"
echo "============================================"
echo ""
echo "Useful commands:"
echo "  kubectl get pods -n ${NAMESPACE}"
echo "  kubectl logs -f deployment/${APP_NAME} -n ${NAMESPACE}"
echo "  kubectl describe deployment/${APP_NAME} -n ${NAMESPACE}"
echo "  kubectl rollout undo deployment/${APP_NAME} -n ${NAMESPACE}  # Rollback"

# Cleanup temp directory
rm -rf "$TEMP_DIR"
