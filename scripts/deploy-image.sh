#!/bin/bash
set -e
set -o pipefail

# ============================================================
# deploy-image.sh - Deploy cargo-tracker to AWS EKS
# ============================================================

APP_NAME="cargo-tracker"
NAMESPACE="cargo-tracker"
MANIFESTS_DIR="kubernetes"

echo "=============================================="
echo "  cargo-tracker - AWS EKS Deployment Script"
echo "=============================================="
echo ""

# ---- Collect deployment inputs ----
read -rp "Enter AWS Region (e.g. us-east-1): " AWS_REGION
if [ -z "$AWS_REGION" ]; then
  echo "ERROR: AWS Region is required."
  exit 1
fi

read -rp "Enter EKS Cluster Name: " CLUSTER_NAME
if [ -z "$CLUSTER_NAME" ]; then
  echo "ERROR: EKS Cluster Name is required."
  exit 1
fi

read -rp "Enter full Docker image URI (e.g. 123456789.dkr.ecr.us-east-1.amazonaws.com/cargo-tracker:latest): " IMAGE_URI
if [ -z "$IMAGE_URI" ]; then
  echo "ERROR: Docker image URI is required."
  exit 1
fi

echo ""
echo "---- Application Configuration ----"
read -rp "Enter GRAPH_TRAVERSAL_URL (or press Enter for default): " GRAPH_TRAVERSAL_URL_INPUT
GRAPH_TRAVERSAL_URL="${GRAPH_TRAVERSAL_URL_INPUT:-http://localhost:8080/cargo-tracker/rest/graph-traversal/shortest-path}"

read -rp "Enter DB_JDBC_URL (or press Enter for embedded H2): " DB_JDBC_URL_INPUT
DB_JDBC_URL="${DB_JDBC_URL_INPUT:-jdbc:h2:file:./cargo-tracker-data/cargo-tracker-database}"

read -rp "Enter DB_USER (or press Enter to skip): " DB_USER_INPUT
DB_USER="${DB_USER_INPUT:-}"

read -rp "Enter DB_PASSWORD (or press Enter to skip): " DB_PASSWORD_INPUT
DB_PASSWORD="${DB_PASSWORD_INPUT:-}"

echo ""
echo "Summary:"
echo "  AWS Region   : $AWS_REGION"
echo "  EKS Cluster  : $CLUSTER_NAME"
echo "  Image URI    : $IMAGE_URI"
echo "  Namespace    : $NAMESPACE"
echo ""

# ---- Configure kubectl for EKS ----
echo "Configuring kubectl for EKS cluster: $CLUSTER_NAME ..."
aws eks update-kubeconfig --region "$AWS_REGION" --name "$CLUSTER_NAME"

echo "Verifying cluster connectivity..."
kubectl cluster-info || { echo "ERROR: Cannot connect to EKS cluster."; exit 1; }

# ---- Prepare manifests (replace placeholders) ----
echo ""
echo "Preparing Kubernetes manifests..."

# Work on copies to avoid modifying originals
DEPLOY_TMP_DIR=$(mktemp -d)
cp -r ${MANIFESTS_DIR}/* "$DEPLOY_TMP_DIR/"

# Replace IMAGE_URI placeholder
sed -i "s|{{IMAGE_URI}}|${IMAGE_URI}|g" "$DEPLOY_TMP_DIR/deployment.yaml"

# Replace application-specific placeholders
sed -i "s|{{GRAPH_TRAVERSAL_URL}}|${GRAPH_TRAVERSAL_URL}|g" "$DEPLOY_TMP_DIR/deployment.yaml"
sed -i "s|{{DB_JDBC_URL}}|${DB_JDBC_URL}|g" "$DEPLOY_TMP_DIR/deployment.yaml"
sed -i "s|{{DB_USER}}|${DB_USER}|g" "$DEPLOY_TMP_DIR/deployment.yaml"
sed -i "s|{{NAMESPACE}}|${NAMESPACE}|g" "$DEPLOY_TMP_DIR/deployment.yaml"

echo "Manifests prepared in: $DEPLOY_TMP_DIR"

# ---- Apply manifests in order ----
echo ""
echo "Applying Kubernetes manifests..."

echo "[1/4] Applying namespace..."
kubectl apply -f "$DEPLOY_TMP_DIR/namespace.yaml"

echo "[2/4] Applying deployment..."
kubectl apply -f "$DEPLOY_TMP_DIR/deployment.yaml"

echo "[3/4] Applying service..."
kubectl apply -f "$DEPLOY_TMP_DIR/service.yaml"

echo "[4/4] Applying ingress..."
kubectl apply -f "$DEPLOY_TMP_DIR/ingress.yaml"

# ---- Wait for rollout ----
echo ""
echo "Waiting for deployment rollout..."
kubectl rollout status deployment/${APP_NAME} -n ${NAMESPACE} --timeout=300s || {
  echo ""
  echo "ERROR: Deployment rollout timed out or failed."
  echo "Run the following to investigate:"
  echo "  kubectl describe deployment/${APP_NAME} -n ${NAMESPACE}"
  echo "  kubectl get pods -n ${NAMESPACE}"
  echo "  kubectl logs -l app=${APP_NAME} -n ${NAMESPACE} --tail=50"
  echo ""
  echo "To rollback:"
  echo "  kubectl rollout undo deployment/${APP_NAME} -n ${NAMESPACE}"
  exit 1
}

# ---- Verify resources ----
echo ""
echo "Verifying deployed resources..."
kubectl get pods,svc,ingress -n ${NAMESPACE}

# ---- Display access URL ----
echo ""
echo "Fetching application URL from ingress..."
INGRESS_HOST=$(kubectl get ingress ${APP_NAME}-ingress -n ${NAMESPACE} -o jsonpath='{.status.loadBalancer.ingress[0].hostname}' 2>/dev/null || echo "pending")
if [ "$INGRESS_HOST" != "pending" ] && [ -n "$INGRESS_HOST" ]; then
  echo ""
  echo "=============================================="
  echo "  Deployment Successful!"
  echo "  Application URL: http://${INGRESS_HOST}"
  echo "=============================================="
else
  echo ""
  echo "=============================================="
  echo "  Deployment Successful!"
  echo "  Ingress hostname is still provisioning."
  echo "  Run: kubectl get ingress -n ${NAMESPACE}"
  echo "=============================================="
fi

# ---- Cleanup temp dir ----
rm -rf "$DEPLOY_TMP_DIR"
