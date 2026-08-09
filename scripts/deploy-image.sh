#!/bin/bash
set -e
set -o pipefail

APP_NAME="cargo-tracker"
NAMESPACE="cargo-tracker"
MANIFEST_DIR="kubernetes"

read -r -p "Enter Azure resource group: " RESOURCE_GROUP
if [ -z "$RESOURCE_GROUP" ]; then
  echo "Azure resource group is required."
  exit 1
fi

read -r -p "Enter AKS cluster name: " CLUSTER_NAME
if [ -z "$CLUSTER_NAME" ]; then
  echo "AKS cluster name is required."
  exit 1
fi

read -r -p "Enter full Docker image URI with tag: " IMAGE_URI
if [ -z "$IMAGE_URI" ]; then
  echo "Docker image URI is required."
  exit 1
fi

read -r -p "Enter value for POSTGRESQL_JDBC_URL (or press Enter to skip): " POSTGRESQL_JDBC_URL
read -r -p "Enter value for POSTGRESQL_USERNAME (or press Enter to skip): " POSTGRESQL_USERNAME
read -r -s -p "Enter value for POSTGRESQL_PASSWORD (or press Enter to skip): " POSTGRESQL_PASSWORD
echo
POSTGRESQL_JDBC_URL=${POSTGRESQL_JDBC_URL:-jdbc:postgresql://postgresql.example.com:5432/postgres}
POSTGRESQL_USERNAME=${POSTGRESQL_USERNAME:-postgres}
POSTGRESQL_PASSWORD=${POSTGRESQL_PASSWORD:-postgres}

TMP_DIR=$(mktemp -d)
trap 'rm -rf "$TMP_DIR"' EXIT
cp -R "$MANIFEST_DIR" "$TMP_DIR/"

sed -i 's|{{IMAGE_URI}}|'"$IMAGE_URI"'|g' "$TMP_DIR/$MANIFEST_DIR/deployment.yaml"
sed -i 's|{{POSTGRESQL_JDBC_URL}}|'"$POSTGRESQL_JDBC_URL"'|g' "$TMP_DIR/$MANIFEST_DIR/deployment.yaml"
sed -i 's|{{POSTGRESQL_USERNAME}}|'"$POSTGRESQL_USERNAME"'|g' "$TMP_DIR/$MANIFEST_DIR/deployment.yaml"
sed -i 's|{{POSTGRESQL_PASSWORD}}|'"$POSTGRESQL_PASSWORD"'|g' "$TMP_DIR/$MANIFEST_DIR/deployment.yaml"

echo "Configuring kubectl for AKS cluster..."
az aks get-credentials --resource-group "$RESOURCE_GROUP" --name "$CLUSTER_NAME" --overwrite-existing

echo "Verifying Kubernetes cluster connectivity..."
kubectl cluster-info

echo "Applying Kubernetes manifests..."
kubectl apply -f "$TMP_DIR/$MANIFEST_DIR/namespace.yaml"
kubectl apply -f "$TMP_DIR/$MANIFEST_DIR/deployment.yaml"
kubectl apply -f "$TMP_DIR/$MANIFEST_DIR/service.yaml"
kubectl apply -f "$TMP_DIR/$MANIFEST_DIR/ingress.yaml"

echo "Waiting for deployment rollout..."
if ! kubectl rollout status deployment/${APP_NAME} -n ${NAMESPACE} --timeout=300s; then
  echo "Deployment rollout failed. To inspect run: kubectl describe deployment/${APP_NAME} -n ${NAMESPACE}"
  echo "Rollback command: kubectl rollout undo deployment/${APP_NAME} -n ${NAMESPACE}"
  exit 1
fi

echo "Deployment resources:"
kubectl get pods,svc,ingress -n ${NAMESPACE}

echo "Ingress hosts:"
kubectl get ingress -n ${NAMESPACE} -o jsonpath='{range .items[*]}{.spec.rules[*].host}{"\n"}{end}'

echo "Deployment completed successfully."
