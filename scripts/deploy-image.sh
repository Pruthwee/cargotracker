#!/bin/bash
set -e
set -o pipefail

APP_NAME="cargo-tracker"
NAMESPACE="cargo-tracker"
MANIFEST_DIR="kubernetes"
DEPLOYMENT_FILE="${MANIFEST_DIR}/deployment.yaml"

require_value() {
  local name="$1"
  local value="$2"
  if [ -z "$value" ]; then
    echo "$name is required."
    exit 1
  fi
}

replace_placeholder() {
  local placeholder="$1"
  local value="$2"
  local file="$3"
  local escaped_value
  escaped_value=$(printf '%s' "$value" | sed 's/[&|\\]/\\&/g')
  sed -i "s|{{${placeholder}}}|${escaped_value}|g" "$file"
}

echo "Azure AKS deployment for ${APP_NAME}"
read -r -p "Enter Azure resource group: " RESOURCE_GROUP
require_value "Azure resource group" "$RESOURCE_GROUP"
read -r -p "Enter AKS cluster name: " CLUSTER_NAME
require_value "AKS cluster name" "$CLUSTER_NAME"
read -r -p "Enter Docker image URI with tag: " IMAGE_URI
require_value "Docker image URI" "$IMAGE_URI"

read -r -p "Enter value for POSTGRESQL_JDBC_URL (or press Enter to skip): " POSTGRESQL_JDBC_URL
read -r -p "Enter value for POSTGRESQL_USERNAME (or press Enter to skip): " POSTGRESQL_USERNAME
read -r -s -p "Enter value for POSTGRESQL_PASSWORD (or press Enter to skip): " POSTGRESQL_PASSWORD
echo
read -r -p "Enter value for GRAPH_TRAVERSAL_URL (or press Enter to skip): " GRAPH_TRAVERSAL_URL

POSTGRESQL_JDBC_URL=${POSTGRESQL_JDBC_URL:-jdbc:postgresql://postgres.example.com:5432/postgres}
POSTGRESQL_USERNAME=${POSTGRESQL_USERNAME:-cargotracker}
POSTGRESQL_PASSWORD=${POSTGRESQL_PASSWORD:-cargotracker}
GRAPH_TRAVERSAL_URL=${GRAPH_TRAVERSAL_URL:-http://cargo-tracker-service/rest/graph-traversal/shortest-path}

echo "Configuring kubectl for AKS cluster..."
az aks get-credentials --resource-group "$RESOURCE_GROUP" --name "$CLUSTER_NAME" --overwrite-existing

echo "Verifying Kubernetes cluster connectivity..."
kubectl cluster-info >/dev/null

echo "Replacing deployment placeholders..."
replace_placeholder "IMAGE_URI" "$IMAGE_URI" "$DEPLOYMENT_FILE"
replace_placeholder "POSTGRESQL_JDBC_URL" "$POSTGRESQL_JDBC_URL" "$DEPLOYMENT_FILE"
replace_placeholder "POSTGRESQL_USERNAME" "$POSTGRESQL_USERNAME" "$DEPLOYMENT_FILE"
replace_placeholder "POSTGRESQL_PASSWORD" "$POSTGRESQL_PASSWORD" "$DEPLOYMENT_FILE"
replace_placeholder "GRAPH_TRAVERSAL_URL" "$GRAPH_TRAVERSAL_URL" "$DEPLOYMENT_FILE"

echo "Applying Kubernetes manifests..."
kubectl apply -f "${MANIFEST_DIR}/namespace.yaml"
kubectl apply -f "${MANIFEST_DIR}/deployment.yaml"
kubectl apply -f "${MANIFEST_DIR}/service.yaml"
kubectl apply -f "${MANIFEST_DIR}/ingress.yaml"

echo "Waiting for rollout to complete..."
if ! kubectl rollout status deployment/${APP_NAME} -n ${NAMESPACE} --timeout=300s; then
  echo "Deployment rollout failed. Recent pod status follows."
  kubectl get pods -n ${NAMESPACE}
  echo "Rollback command: kubectl rollout undo deployment/${APP_NAME} -n ${NAMESPACE}"
  exit 1
fi

echo "Deployment resources:"
kubectl get pods,svc,ingress -n ${NAMESPACE}

echo "Ingress URL information:"
kubectl get ingress ${APP_NAME}-ingress -n ${NAMESPACE} -o jsonpath='{.spec.rules[0].host}' || true
echo

echo "Deployment completed successfully."
