#!/bin/bash
set -e
set -o pipefail

APP_NAME="cargo-tracker"
NAMESPACE="cargo-tracker"
MANIFEST_DIR="kubernetes"

echo "Cargo Tracker Azure AKS deployment"
read -r -p "Enter Azure resource group: " RESOURCE_GROUP
read -r -p "Enter AKS cluster name: " CLUSTER_NAME
read -r -p "Enter Docker image URI (registry/repository:tag): " IMAGE_URI
read -r -p "Enter PostgreSQL JDBC URL (or press Enter to skip): " POSTGRESQL_JDBC_URL
read -r -p "Enter PostgreSQL username (or press Enter to skip): " POSTGRESQL_USERNAME
read -r -s -p "Enter PostgreSQL password (or press Enter to skip secret creation): " POSTGRESQL_PASSWORD
echo
read -r -p "Enter Graph Traversal URL (default http://localhost:8080/rest/graph-traversal/shortest-path): " GRAPH_TRAVERSAL_URL

if [ -z "$RESOURCE_GROUP" ] || [ -z "$CLUSTER_NAME" ] || [ -z "$IMAGE_URI" ]; then
  echo "Resource group, cluster name, and image URI are required" >&2
  exit 1
fi

POSTGRESQL_JDBC_URL=${POSTGRESQL_JDBC_URL:-jdbc:postgresql://postgres.default.svc.cluster.local:5432/postgres}
POSTGRESQL_USERNAME=${POSTGRESQL_USERNAME:-postgres}
GRAPH_TRAVERSAL_URL=${GRAPH_TRAVERSAL_URL:-http://localhost:8080/rest/graph-traversal/shortest-path}

TMP_DIR=$(mktemp -d)
trap 'rm -rf "$TMP_DIR"' EXIT
cp -R "$MANIFEST_DIR" "$TMP_DIR/"
WORK_MANIFEST_DIR="$TMP_DIR/$MANIFEST_DIR"

echo "Preparing manifests"
sed -i 's|{{IMAGE_URI}}|'"$IMAGE_URI"'|g' "$WORK_MANIFEST_DIR/deployment.yaml"
sed -i 's|{{POSTGRESQL_JDBC_URL}}|'"$POSTGRESQL_JDBC_URL"'|g' "$WORK_MANIFEST_DIR/deployment.yaml"
sed -i 's|{{POSTGRESQL_USERNAME}}|'"$POSTGRESQL_USERNAME"'|g' "$WORK_MANIFEST_DIR/deployment.yaml"
sed -i 's|{{GRAPH_TRAVERSAL_URL}}|'"$GRAPH_TRAVERSAL_URL"'|g' "$WORK_MANIFEST_DIR/deployment.yaml"

echo "Configuring kubectl for AKS cluster"
az aks get-credentials --resource-group "$RESOURCE_GROUP" --name "$CLUSTER_NAME" --overwrite-existing

echo "Verifying cluster connectivity"
kubectl cluster-info

echo "Applying namespace"
kubectl apply -f "$WORK_MANIFEST_DIR/namespace.yaml"

if [ -n "$POSTGRESQL_PASSWORD" ]; then
  echo "Creating or updating application secret"
  kubectl create secret generic cargo-tracker-secrets \
    --namespace "$NAMESPACE" \
    --from-literal=postgresql-password="$POSTGRESQL_PASSWORD" \
    --dry-run=client -o yaml | kubectl apply -f -
else
  echo "PostgreSQL password skipped. Ensure secret cargo-tracker-secrets with key postgresql-password exists if required."
fi

echo "Applying deployment, service, and ingress"
kubectl apply -f "$WORK_MANIFEST_DIR/deployment.yaml"
kubectl apply -f "$WORK_MANIFEST_DIR/service.yaml"
kubectl apply -f "$WORK_MANIFEST_DIR/ingress.yaml"

echo "Waiting for rollout"
kubectl rollout status deployment/$APP_NAME -n "$NAMESPACE" --timeout=300s

echo "Deployment resources"
kubectl get pods,svc,ingress -n "$NAMESPACE"

echo "Application URL"
kubectl get ingress cargo-tracker-ingress -n "$NAMESPACE" -o jsonpath='{.spec.rules[0].host}' || true
echo

echo "Rollback if needed: kubectl rollout undo deployment/$APP_NAME -n $NAMESPACE"
