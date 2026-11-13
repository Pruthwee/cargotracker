@echo off
setlocal enabledelayedexpansion

echo === Cargo Tracker Deploy to AWS EKS ===

set "PROJECT_NAME=Cargo-MONO-1"

:: Sanitize image name
for /f "delims=" %%i in ('powershell -command "$n = '%PROJECT_NAME%'.ToLower(); ($n -replace '[^a-z0-9]+', '-') -replace '^-+|-+$', ''") do set "IMAGE_NAME=%%i"

:: Ask for AWS region
set /p AWS_REGION="Enter AWS region (e.g., us-east-1): "
if "!AWS_REGION!"=="" (
  echo AWS region is required.
  exit /b 1
)

:: Ask for EKS cluster name
set /p CLUSTER_NAME="Enter EKS cluster name: "
if "!CLUSTER_NAME!"=="" (
  echo EKS cluster name is required.
  exit /b 1
)

:: Ask for image URI
set /p IMAGE_URI="Enter full image URI (e.g., 123456789.dkr.ecr.us-east-1.amazonaws.com/cargo-mono-1:latest): "
if "!IMAGE_URI!"=="" (
  echo Image URI is required.
  exit /b 1
)

:: Ask for environment variables
set /p DB_JDBC_URL="Enter value for DB_JDBC_URL (or press Enter to skip): "
set /p GRAPH_TRAVERSAL_URL="Enter value for GRAPH_TRAVERSAL_URL (or press Enter to skip): "
set /p DB_USER="Enter value for DB_USER (credential): "
set /p DB_PASSWORD="Enter value for DB_PASSWORD (credential): "

:: Configure kubectl for EKS
echo Configuring kubectl for EKS cluster: !CLUSTER_NAME! in region !AWS_REGION!
call aws eks update-kubeconfig --region !AWS_REGION! --name !CLUSTER_NAME!
if !ERRORLEVEL! neq 0 (
  echo Failed to configure kubectl
  exit /b 1
)

:: Verify cluster connectivity
kubectl cluster-info
if !ERRORLEVEL! neq 0 (
  echo Failed to connect to EKS cluster
  exit /b 1
)

:: Create namespace
echo Creating namespace...
kubectl apply -f kubernetes/namespace.yaml
if !ERRORLEVEL! neq 0 (
  echo Failed to create namespace
  exit /b 1
)

:: Create Secret for credentials
echo Creating Kubernetes Secret for credentials...
kubectl create secret generic cargo-tracker-secrets -n cargo-tracker --from-literal=db-user="!DB_USER!" --from-literal=db-password="!DB_PASSWORD!" --dry-run=client -o yaml | kubectl apply -f -
if !ERRORLEVEL! neq 0 (
  echo Failed to create secret
  exit /b 1
)

:: Replace placeholders in deployment.yaml
echo Replacing placeholders in deployment manifests...
powershell -Command "(gc kubernetes/deployment.yaml) -replace '{{IMAGE_URI}}', '!IMAGE_URI!' | Out-File -encoding UTF8 kubernetes/deployment.yaml"
powershell -Command "(gc kubernetes/deployment.yaml) -replace '{{NAMESPACE}}', 'cargo-tracker' | Out-File -encoding UTF8 kubernetes/deployment.yaml"
powershell -Command "(gc kubernetes/deployment.yaml) -replace '{{DB_JDBC_URL}}', '!DB_JDBC_URL!' | Out-File -encoding UTF8 kubernetes/deployment.yaml"
powershell -Command "(gc kubernetes/deployment.yaml) -replace '{{GRAPH_TRAVERSAL_URL}}', '!GRAPH_TRAVERSAL_URL!' | Out-File -encoding UTF8 kubernetes/deployment.yaml"

powershell -Command "(gc kubernetes/service.yaml) -replace '{{NAMESPACE}}', 'cargo-tracker' | Out-File -encoding UTF8 kubernetes/service.yaml"
powershell -Command "(gc kubernetes/ingress.yaml) -replace '{{NAMESPACE}}', 'cargo-tracker' | Out-File -encoding UTF8 kubernetes/ingress.yaml"

:: Apply deployment
echo Applying deployment...
kubectl apply -f kubernetes/deployment.yaml
if !ERRORLEVEL! neq 0 (
  echo Failed to apply deployment
  exit /b 1
)

:: Apply service
echo Applying service...
kubectl apply -f kubernetes/service.yaml
if !ERRORLEVEL! neq 0 (
  echo Failed to apply service
  exit /b 1
)

:: Apply ingress
echo Applying ingress...
kubectl apply -f kubernetes/ingress.yaml
if !ERRORLEVEL! neq 0 (
  echo Failed to apply ingress
  exit /b 1
)

:: Wait for rollout
echo Waiting for deployment rollout...
kubectl rollout status deployment/cargo-tracker -n cargo-tracker
if !ERRORLEVEL! neq 0 (
  echo Deployment rollout failed
  exit /b 1
)

:: Show resources
echo.
echo === Deployed resources ===
kubectl get pods,svc,ingress -n cargo-tracker

:: Show application URL
echo.
echo === Application URL ===
echo https://cargo-tracker.example.com

echo.
echo === Deployment complete ===

endlocal
