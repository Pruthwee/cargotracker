#!/bin/bash
set -e
set -o pipefail

# ============================================================
# deploy-image.sh  –  Deploy cargo-tracker to AWS EKS
# ============================================================

APP_NAME="cargo-tracker"
NAMESPACE="cargo-tracker"
K8S_DIR="kubernetes"

echo "=============================================="
echo "  cargo-tracker  –  Deploy to AWS EKS"
echo "=============================================="

# -------------------------------------------------------
# Collect deployment parameters
# -------------------------------------------------------
read -rp "Enter AWS region [us-east-1]: " AWS_REGION
AWS_REGION="${AWS_REGION:-us-east-1}"

read -rp "Enter EKS cluster name: " CLUSTER_NAME
if [ -z "$CLUSTER_NAME" ]; then
  echo "ERROR: EKS cluster name is required." >&2
  exit 1
fi

read -rp "Enter full Docker image URI (e.g. 123456789.dkr.ecr.us-east-1.amazonaws.com/cargo-tracker:latest): " IMAGE_URI
if [ -z "$IMAGE_URI" ]; then
  echo "ERROR: Docker image URI is required." >&2
  exit 1
fi

echo ""
echo "--- Optional environment variable configuration ---"
echo "(Press Enter to skip any variable)"

read -rp "Enter REDIS_HOST (ElastiCache endpoint) [localhost]: " REDIS_HOST
REDIS_HOST="${REDIS_HOST:-localhost}"

read -rp "Enter REDIS_PORT [6379]: " REDIS_PORT
REDIS_PORT="${REDIS_PORT:-6379}"

read -rsp "Enter REDIS_PASSWORD (leave blank if none): " REDIS_PASSWORD
echo ""

read -rp "Enter DB_JDBC_URL (PostgreSQL JDBC URL) [jdbc:h2:file:./cargo-tracker-data/cargo-tracker-database]: " DB_JDBC_URL
DB_JDBC_URL="${DB_JDBC_URL:-jdbc:h2:file:./cargo-tracker-data/cargo-tracker-database}"

read -rp "Enter DB_USER: " DB_USER
DB_USER="${DB_USER:-}"

read -rsp "Enter DB_PASSWORD: " DB_PASSWORD
echo ""
DB_PASSWORD="${DB_PASSWORD:-}"

read -rp "Enter GRAPH_TRAVERSAL_URL [http://localhost:8080/cargo-tracker/rest/graph-traversal/shortest-path]: " GRAPH_TRAVERSAL_URL
GRAPH_TRAVERSAL_URL="${GRAPH_TRAVERSAL_URL:-http://localhost:8080/cargo-tracker/rest/graph-traversal/shortest-path}"

# -------------------------------------------------------
# Configure kubectl for EKS
# -------------------------------------------------------
echo ""
echo "Configuring kubectl for EKS cluster: $CLUSTER_NAME ..."
aws eks update-kubeconfig --region "$AWS_REGION" --name "$CLUSTER_NAME"

echo "Verifying cluster connectivity..."
kubectl cluster-info || { echo "ERROR: Cannot connect to EKS cluster." >&2; exit 1; }

# -------------------------------------------------------
# Update Kubernetes manifests with actual values
# -------------------------------------------------------
echo ""
echo "Updating Kubernetes manifests..."

# Work on copies to avoid modifying originals
TMP_DIR=$(mktemp -d)
cp -r "$K8S_DIR"/* "$TMP_DIR"/

sed -i "s|{{IMAGE_URI}}|${IMAGE_URI}|g"                         "$TMP_DIR/deployment.yaml"
sed -i "s|{{REDIS_HOST}}|${REDIS_HOST}|g"                       "$TMP_DIR/deployment.yaml"
sed -i "s|{{REDIS_PORT}}|${REDIS_PORT}|g"                       "$TMP_DIR/deployment.yaml"
sed -i "s|{{REDIS_PASSWORD}}|${REDIS_PASSWORD}|g"               "$TMP_DIR/deployment.yaml"
sed -i "s|{{DB_JDBC_URL}}|${DB_JDBC_URL}|g"                     "$TMP_DIR/deployment.yaml"
sed -i "s|{{DB_USER}}|${DB_USER}|g"                             "$TMP_DIR/deployment.yaml"
sed -i "s|{{DB_PASSWORD}}|${DB_PASSWORD}|g"                     "$TMP_DIR/deployment.yaml"
sed -i "s|{{GRAPH_TRAVERSAL_URL}}|${GRAPH_TRAVERSAL_URL}|g"     "$TMP_DIR/deployment.yaml"

# -------------------------------------------------------
# Apply manifests in order
# -------------------------------------------------------
echo ""
echo "Applying Kubernetes manifests..."

echo "  [1/4] Applying namespace..."
kubectl apply -f "$TMP_DIR/namespace.yaml"

echo "  [2/4] Applying deployment..."
kubectl apply -f "$TMP_DIR/deployment.yaml"

echo "  [3/4] Applying service..."
kubectl apply -f "$TMP_DIR/service.yaml"

echo "  [4/4] Applying ingress..."
kubectl apply -f "$TMP_DIR/ingress.yaml"

# -------------------------------------------------------
# Wait for rollout
# -------------------------------------------------------
echo ""
echo "Waiting for deployment rollout..."
kubectl rollout status deployment/"$APP_NAME" -n "$NAMESPACE" --timeout=300s

# -------------------------------------------------------
# Verify resources
# -------------------------------------------------------
echo ""
echo "Verifying deployed resources..."
kubectl get pods,svc,ingress -n "$NAMESPACE"

# -------------------------------------------------------
# Display access URL
# -------------------------------------------------------
echo ""
echo "Fetching application URL..."
INGRESS_HOST=$(kubectl get ingress cargo-tracker-ingress -n "$NAMESPACE" \
  -o jsonpath='{.status.loadBalancer.ingress[0].hostname}' 2>/dev/null || echo "")
if [ -n "$INGRESS_HOST" ]; then
  echo "Application URL: http://${INGRESS_HOST}/cargo-tracker"
else
  echo "Ingress hostname not yet assigned. Run the following to check:"
  echo "  kubectl get ingress -n $NAMESPACE"
fi

# -------------------------------------------------------
# Cleanup temp files
# -------------------------------------------------------
rm -rf "$TMP_DIR"

echo ""
echo "=============================================="
echo "  Deployment complete!"
echo "  Namespace : $NAMESPACE"
echo "  Image     : $IMAGE_URI"
echo "=============================================="
echo ""
echo "Rollback command (if needed):"
echo "  kubectl rollout undo deployment/$APP_NAME -n $NAMESPACE"
