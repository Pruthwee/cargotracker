@echo off
setlocal enabledelayedexpansion

echo === Cargo Tracker - Deploy to AWS EKS ===

set "PROJECT_NAME=cargo-tracker"
set "IMAGE_NAME=cargo-tracker"

set /p AWS_REGION="Enter AWS region (e.g., us-east-1): "
if "!AWS_REGION!"=="" (
  echo AWS region is required.
  exit /b 1
)

set /p CLUSTER_NAME="Enter EKS cluster name: "
if "!CLUSTER_NAME!"=="" (
  echo EKS cluster name is required.
  exit /b 1
)

set /p IMAGE_URI="Enter full image URI (registry/repo:tag): "
if "!IMAGE_URI!"=="" (
  echo Image URI is required.
  exit /b 1
)

echo.
echo Enter values for environment variables (press Enter to skip and use placeholder):
echo.

set /p DB_JDBC_URL_VAL="Enter value for DB_JDBC_URL (or press Enter to skip): "
set /p DB_USER_VAL="Enter value for DB_USER (or press Enter to skip): "
set /p DB_PASSWORD_VAL="Enter value for DB_PASSWORD (hidden): "
echo.
set /p GRAPH_TRAVERSAL_URL_VAL="Enter value for GRAPH_TRAVERSAL_URL (or press Enter to skip): "

echo.
echo === Configuring kubectl ===
aws eks update-kubeconfig --region !AWS_REGION! --name !CLUSTER_NAME!
if !ERRORLEVEL! neq 0 (
  echo Failed to configure kubectl
  exit /b 1
)

echo.
echo === Checking cluster connectivity ===
kubectl cluster-info
if !ERRORLEVEL! neq 0 (
  echo Cluster connectivity check failed
  exit /b 1
)

echo.
echo === Creating namespace ===
kubectl apply -f kubernetes\namespace.yaml
if !ERRORLEVEL! neq 0 (
  echo Namespace creation failed
  exit /b 1
)

echo.
echo === Creating secrets ===
if not "!DB_USER_VAL!"=="" if not "!DB_PASSWORD_VAL!"=="" (
  kubectl create secret generic cargo-tracker-secrets -n cargo-tracker --from-literal=db-user="!DB_USER_VAL!" --from-literal=db-password="!DB_PASSWORD_VAL!" --dry-run=client -o yaml | kubectl apply -f -
  if !ERRORLEVEL! neq 0 (
    echo Secret creation failed
    exit /b 1
  )
) else (
  echo Skipping secret creation ^(DB_USER or DB_PASSWORD not provided^)
)

echo.
echo === Deploying application ===
copy kubernetes\deployment.yaml kubernetes\deployment.yaml.bak >nul 2>&1

powershell -Command "(Get-Content kubernetes\deployment.yaml) -replace '{{IMAGE_URI}}', '!IMAGE_URI!' | Set-Content kubernetes\deployment.yaml"
if !ERRORLEVEL! neq 0 (
  echo Failed to replace IMAGE_URI placeholder
  exit /b 1
)

if not "!DB_JDBC_URL_VAL!"=="" (
  powershell -Command "(Get-Content kubernetes\deployment.yaml) -replace '{{DB_JDBC_URL}}', '!DB_JDBC_URL_VAL!' | Set-Content kubernetes\deployment.yaml"
)
if not "!DB_USER_VAL!"=="" (
  powershell -Command "(Get-Content kubernetes\deployment.yaml) -replace '{{DB_USER}}', '!DB_USER_VAL!' | Set-Content kubernetes\deployment.yaml"
)
if not "!GRAPH_TRAVERSAL_URL_VAL!"=="" (
  powershell -Command "(Get-Content kubernetes\deployment.yaml) -replace '{{GRAPH_TRAVERSAL_URL}}', '!GRAPH_TRAVERSAL_URL_VAL!' | Set-Content kubernetes\deployment.yaml"
)

del kubernetes\deployment.yaml.bak >nul 2>&1

kubectl apply -f kubernetes\deployment.yaml
if !ERRORLEVEL! neq 0 (
  echo Deployment failed
  exit /b 1
)

echo.
echo === Creating service ===
kubectl apply -f kubernetes\service.yaml
if !ERRORLEVEL! neq 0 (
  echo Service creation failed
  exit /b 1
)

echo.
echo === Creating ingress ===
kubectl apply -f kubernetes\ingress.yaml
if !ERRORLEVEL! neq 0 (
  echo Ingress creation failed
  exit /b 1
)

echo.
echo === Waiting for deployment rollout ===
kubectl rollout status deployment/cargo-tracker -n cargo-tracker
if !ERRORLEVEL! neq 0 (
  echo Deployment rollout failed
  exit /b 1
)

echo.
echo === Deployment status ===
kubectl get pods,svc,ingress -n cargo-tracker

echo.
echo === Application URL ===
for /f "delims=" %%i in ('kubectl get ingress cargo-tracker-ingress -n cargo-tracker -o jsonpath^="{.status.loadBalancer.ingress[0].hostname}"') do set "APP_URL=%%i"
echo !APP_URL!

echo.
echo === Deployment complete ===
echo If the deployment fails, roll back with:
echo   kubectl rollout undo deployment/cargo-tracker -n cargo-tracker

endlocal
