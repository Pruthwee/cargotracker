#!/bin/bash
# Deploys the pushed cargotrackerM-Mono image to AWS EKS with kubectl.
# Run it from the repository root: ./scripts/deploy-image.sh
set -e
set -o pipefail

APP_NAME="cargotrackerm-mono"
NAMESPACE="cargotrackerm-mono"

command -v aws >/dev/null 2>&1 || { echo "ERROR: the AWS CLI is not installed or not in PATH"; exit 1; }
command -v kubectl >/dev/null 2>&1 || { echo "ERROR: kubectl is not installed or not in PATH"; exit 1; }

# Escapes a value for the replacement part of a sed command that uses | as its delimiter
sed_escape() {
  printf '%s' "$1" | sed -e 's/[\\&|]/\\&/g'
}

echo "=== AWS EKS deployment of $APP_NAME ==="
read -r -p "Enter the AWS region (for example us-east-1): " AWS_REGION
read -r -p "Enter the EKS cluster name: " CLUSTER_NAME
read -r -p "Enter the image URI (registry path and tag, for example 123456789012.dkr.ecr.us-east-1.amazonaws.com/$APP_NAME:latest): " IMAGE_URI

if [ -z "$AWS_REGION" ] || [ -z "$CLUSTER_NAME" ] || [ -z "$IMAGE_URI" ]; then
  echo "ERROR: the AWS region, the cluster name and the image URI are required"
  exit 1
fi

read -r -p "Enter value for DB_JDBC_URL (or press Enter to skip) (for example jdbc:postgresql://my-rds-host:5432/postgres): " DB_JDBC_URL
read -r -p "Enter value for DB_USER (or press Enter to skip): " DB_USER
read -r -s -p "Enter value for DB_PASSWORD (hidden, or press Enter to skip): " DB_PASSWORD
echo ""

echo "Configuring kubectl for the cluster $CLUSTER_NAME ..."
if ! aws eks update-kubeconfig --region "$AWS_REGION" --name "$CLUSTER_NAME"; then
  echo "ERROR: could not configure kubectl for the cluster. Check the region, the cluster name and your IAM permissions."
  exit 1
fi
kubectl cluster-info || exit 1

# The manifests are filled in place; the originals are restored when the script ends
BACKUP_DIR=$(mktemp -d)
cp kubernetes/*.yaml "$BACKUP_DIR"/
restore_manifests() {
  cp "$BACKUP_DIR"/*.yaml kubernetes/
  rm -f kubernetes/*.bak
  rm -rf "$BACKUP_DIR"
}
trap restore_manifests EXIT

IMAGE_URI_ESC=$(sed_escape "$IMAGE_URI")
DB_JDBC_URL_ESC=$(sed_escape "$DB_JDBC_URL")
NAMESPACE_ESC=$(sed_escape "$NAMESPACE")

for f in kubernetes/namespace.yaml kubernetes/deployment.yaml kubernetes/service.yaml kubernetes/ingress.yaml; do
  sed -i.bak 's|{{IMAGE_URI}}|'"$IMAGE_URI_ESC"'|g' "$f"
  sed -i.bak 's|{{NAMESPACE}}|'"$NAMESPACE_ESC"'|g' "$f"
  sed -i.bak 's|{{DB_JDBC_URL}}|'"$DB_JDBC_URL_ESC"'|g' "$f"
  rm -f "$f.bak"
done

echo "Applying the namespace ..."
kubectl apply -f kubernetes/namespace.yaml || { echo "ERROR: could not apply the namespace"; exit 1; }

echo "Creating the Secret $APP_NAME-secrets ..."
kubectl create secret generic "$APP_NAME-secrets" -n "$NAMESPACE" \
  --from-literal=DB_USER="$DB_USER" \
  --from-literal=DB_PASSWORD="$DB_PASSWORD" \
  --dry-run=client -o yaml | kubectl apply -f - || { echo "ERROR: could not create the Secret"; exit 1; }

echo "Applying the deployment, the service and the ingress ..."
kubectl apply -f kubernetes/deployment.yaml || { echo "ERROR: could not apply deployment.yaml"; exit 1; }
kubectl apply -f kubernetes/service.yaml || { echo "ERROR: could not apply service.yaml"; exit 1; }
kubectl apply -f kubernetes/ingress.yaml || { echo "ERROR: could not apply ingress.yaml"; exit 1; }

echo "Waiting for the rollout ..."
if ! kubectl rollout status deployment/$APP_NAME -n $NAMESPACE --timeout=600s; then
  echo "ERROR: the rollout failed."
  echo "Rollback with: kubectl rollout undo deployment/$APP_NAME -n $NAMESPACE"
  echo "Inspect with: kubectl describe pods -n $NAMESPACE  and  kubectl logs deployment/$APP_NAME -n $NAMESPACE"
  exit 1
fi

kubectl get pods,svc,ingress -n $NAMESPACE

INGRESS_HOST=""
for i in 1 2 3 4 5 6 7 8 9 10 11 12; do
  INGRESS_HOST=$(kubectl get ingress $APP_NAME-ingress -n $NAMESPACE -o jsonpath='{.status.loadBalancer.ingress[0].hostname}' 2>/dev/null || true)
  [ -n "$INGRESS_HOST" ] && break
  sleep 10
done

if [ -n "$INGRESS_HOST" ]; then
  echo "Application URL: http://$INGRESS_HOST/ (health check: http://$INGRESS_HOST/rest/health)"
else
  echo "The load balancer address is not ready yet. Check later with: kubectl get ingress -n $NAMESPACE"
fi

echo "To roll back: kubectl rollout undo deployment/$APP_NAME -n $NAMESPACE"
