@echo off
setlocal enabledelayedexpansion

echo === Cargo Tracker EKS Deployment Script ===
echo.

set PROJECT_NAME=cargo-tracker
set IMAGE_NAME=cargo-tracker

set /p AWS_REGION="Enter AWS region (e.g., us-east-1): "
set /p CLUSTER_NAME="Enter EKS cluster name: "
set /p IMAGE_URI="Enter image URI (full path with registry and tag, e.g., 123456789.dkr.ecr.us-east-1.amazonaws.com/cargo-tracker:latest): "

echo.
echo === Environment Variables ===
echo Enter values for the application's environment variables.
echo Press Enter to skip (use the placeholder default).
echo.

set /p GRAPH_TRAVERSAL_URL="Enter value for GRAPH_TRAVERSAL_URL (or press Enter to skip): "
if "!GRAPH_TRAVERSAL_URL!"=="" (
  set GRAPH_TRAVERSAL_URL=http://cargo-tracker:8080/rest/graph-traversal/shortest-path
)

echo.
echo === Credentials ===
echo These will be stored in a Kubernetes Secret.
echo.

set /p DB_URL="Enter DB URL (or press Enter to skip): "
set /p DB_USER="Enter DB user (or press Enter to skip): "
set /p DB_PASSWORD="Enter DB password (or press Enter to skip): "

echo.
echo === Configuring kubectl for EKS ===
call aws eks update-kubeconfig --region !AWS_REGION! --name !CLUSTER_NAME!
if !ERRORLEVEL! neq 0 (
  echo Failed to configure kubectl
  exit /b 1
)
echo kubectl configured for cluster: !CLUSTER_NAME!

echo.
echo === Verifying cluster connectivity ===
call kubectl cluster-info
if !ERRORLEVEL! neq 0 (
  echo Failed to connect to cluster
  exit /b 1
)
echo Cluster connectivity verified.

echo.
echo === Applying namespace ===
call kubectl apply -f kubernetes/namespace.yaml
if !ERRORLEVEL! neq 0 (
  echo Failed to apply namespace
  exit /b 1
)
echo Namespace applied.

echo.
echo === Creating Kubernetes Secret ===
set SECRET_ARGS=
if not "!DB_URL!"=="" (
  set SECRET_ARGS=!SECRET_ARGS! --from-literal=db-url=!DB_URL!
)
if not "!DB_USER!"=="" (
  set SECRET_ARGS=!SECRET_ARGS! --from-literal=db-user=!DB_USER!
)
if not "!DB_PASSWORD!"=="" (
  set SECRET_ARGS=!SECRET_ARGS! --from-literal=db-password=!DB_PASSWORD!
)

if not "!SECRET_ARGS!"=="" (
  call kubectl create secret generic cargo-tracker-secrets -n cargo-tracker !SECRET_ARGS! --dry-run=client -o yaml | kubectl apply -f -
  if !ERRORLEVEL! neq 0 (
    echo Failed to create secret
    exit /b 1
  )
  echo Secret created.
) else (
  echo No credentials provided, skipping Secret creation.
)

echo.
echo === Deploying application ===
REM Replace placeholders in deployment.yaml
powershell -Command "(Get-Content kubernetes/deployment.yaml) -replace '{{IMAGE_URI}}', '!IMAGE_URI!' | Set-Content kubernetes/deployment.yaml"
powershell -Command "(Get-Content kubernetes/deployment.yaml) -replace '{{NAMESPACE}}', 'cargo-tracker' | Set-Content kubernetes/deployment.yaml"
powershell -Command "(Get-Content kubernetes/deployment.yaml) -replace '{{GRAPH_TRAVERSAL_URL}}', '!GRAPH_TRAVERSAL_URL!' | Set-Content kubernetes/deployment.yaml"

call kubectl apply -f kubernetes/deployment.yaml
if !ERRORLEVEL! neq 0 (
  echo Failed to apply deployment
  exit /b 1
)
echo Deployment applied.

call kubectl apply -f kubernetes/service.yaml
if !ERRORLEVEL! neq 0 (
  echo Failed to apply service
  exit /b 1
)
echo Service applied.

call kubectl apply -f kubernetes/ingress.yaml
if !ERRORLEVEL! neq 0 (
  echo Failed to apply ingress
  exit /b 1
)
echo Ingress applied.

echo.
echo === Waiting for deployment rollout ===
call kubectl rollout status deployment/cargo-tracker -n cargo-tracker
if !ERRORLEVEL! neq 0 (
  echo Deployment rollout failed
  exit /b 1
)
echo Deployment rollout complete.

echo.
echo === Deployed Resources ===
call kubectl get pods,svc,ingress -n cargo-tracker

echo.
echo === Application URL ===
echo http://cargo-tracker.example.com

echo.
echo === Deployment Complete ===
echo If you need to rollback, run:
echo   kubectl rollout undo deployment/cargo-tracker -n cargo-tracker

endlocal
