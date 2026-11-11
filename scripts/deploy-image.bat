@echo off
setlocal enabledelayedexpansion

set PROJECT_NAME=cargo-tracker
set IMAGE_NAME=cargo-tracker

echo === Cargo Tracker EKS Deployment ===
echo Project name: %PROJECT_NAME%
echo Image name: %IMAGE_NAME%

echo.
echo === AWS EKS Configuration ===
set /p AWS_REGION="Enter AWS region: "
set /p CLUSTER_NAME="Enter EKS cluster name: "
set /p IMAGE_URI="Enter image URI (full path with registry and tag): "
set /p DB_JDBC_URL="Enter DB JDBC URL (or press Enter to skip): "
set /p DB_USER="Enter DB user (or press Enter to skip): "
set /p DB_PASSWORD="Enter DB password: "
set /p GRAPH_TRAVERSAL_URL="Enter Graph Traversal URL (or press Enter to skip): "

echo.
echo === Configuring kubectl for EKS ===
aws eks update-kubeconfig --region !AWS_REGION! --name !CLUSTER_NAME!
if !ERRORLEVEL! neq 0 (
  echo kubectl configuration failed
  exit /b 1
)
kubectl cluster-info
if !ERRORLEVEL! neq 0 (
  echo kubectl cluster-info failed
  exit /b 1
)
echo kubectl configured for cluster: !CLUSTER_NAME!

echo.
echo === Creating Kubernetes Secret ===
kubectl create secret generic cargo-tracker-secrets -n cargo-tracker --from-literal=db_password="!DB_PASSWORD!" --dry-run=client -o yaml | kubectl apply -f -
if !ERRORLEVEL! neq 0 (
  echo Secret creation failed
  exit /b 1
)
echo Secret created: cargo-tracker-secrets

echo.
echo === Applying namespace ===
kubectl apply -f kubernetes/namespace.yaml
if !ERRORLEVEL! neq 0 (
  echo Namespace application failed
  exit /b 1
)

echo.
echo === Replacing placeholders in deployment.yaml ===
powershell -Command "(gc kubernetes/deployment.yaml) -replace '{{IMAGE_URI}}', '!IMAGE_URI!' | Out-File -encoding ASCII kubernetes/deployment.yaml"
powershell -Command "(gc kubernetes/deployment.yaml) -replace '{{DB_JDBC_URL}}', '!DB_JDBC_URL!' | Out-File -encoding ASCII kubernetes/deployment.yaml"
powershell -Command "(gc kubernetes/deployment.yaml) -replace '{{DB_USER}}', '!DB_USER!' | Out-File -encoding ASCII kubernetes/deployment.yaml"
powershell -Command "(gc kubernetes/deployment.yaml) -replace '{{GRAPH_TRAVERSAL_URL}}', '!GRAPH_TRAVERSAL_URL!' | Out-File -encoding ASCII kubernetes/deployment.yaml"

echo.
echo === Applying deployment, service, and ingress ===
kubectl apply -f kubernetes/deployment.yaml
if !ERRORLEVEL! neq 0 (
  echo Deployment application failed
  exit /b 1
)
kubectl apply -f kubernetes/service.yaml
if !ERRORLEVEL! neq 0 (
  echo Service application failed
  exit /b 1
)
kubectl apply -f kubernetes/ingress.yaml
if !ERRORLEVEL! neq 0 (
  echo Ingress application failed
  exit /b 1
)

echo.
echo === Waiting for deployment rollout ===
kubectl rollout status deployment/cargo-tracker -n cargo-tracker
if !ERRORLEVEL! neq 0 (
  echo Rollout failed
  exit /b 1
)

echo.
echo === Deployment status ===
kubectl get pods,svc,ingress -n cargo-tracker

echo.
echo === Application URL ===
kubectl get ingress cargo-tracker-ingress -n cargo-tracker -o jsonpath="{.status.loadBalancer.ingress[0].hostname}"

echo.
echo === Deployment complete ===
echo If the deployment fails, rollback with:
echo   kubectl rollout undo deployment/cargo-tracker -n cargo-tracker

endlocal
