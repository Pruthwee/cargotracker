@echo off
setlocal enabledelayedexpansion

REM Deploy script for cargo-tracker on AWS EKS
REM Usage: scripts\deploy-image.bat

set PROJECT_NAME=cargotrackerMMono
for /f "delims=" %%i in ('powershell -command "$n='cargotrackerMMono'.ToLower(); $n -creplace '[^a-z0-9]+','-'; $n.Trim('-')"') do set IMAGE_NAME=%%i
set NAMESPACE=cargo-tracker

echo === Cargo Tracker EKS Deployment ===
echo Project name: %PROJECT_NAME%
echo Image name: %IMAGE_NAME%
echo Namespace: %NAMESPACE%
echo.

set /p AWS_REGION="Enter AWS region (e.g., us-east-1): "
set /p CLUSTER_NAME="Enter EKS cluster name: "
set /p IMAGE_URI="Enter full image URI (registry/repo:tag): "

echo.
echo === Configuration ===
echo AWS Region: !AWS_REGION!
echo EKS Cluster: !CLUSTER_NAME!
echo Image URI: !IMAGE_URI!
echo Namespace: !NAMESPACE!
echo.

echo === Environment Variables ===
echo Enter values for the application's environment variables.
echo Press Enter to skip (use placeholder).
echo.

set /p DB_URL_VALUE="Enter value for DB_URL (or press Enter to skip): "
set /p GRAPH_TRAVERSAL_URL_VALUE="Enter value for GRAPH_TRAVERSAL_URL (or press Enter to skip): "

echo.
echo === Credentials ===
echo Enter values for credentials (input will not be echoed).
echo.

call :read_password DB_USER_VALUE "Enter DB_USER: "
call :read_password DB_PASSWORD_VALUE "Enter DB_PASSWORD: "

echo.
echo === Configuring kubectl for EKS ===
call aws eks update-kubeconfig --region !AWS_REGION! --name !CLUSTER_NAME!
if !ERRORLEVEL! neq 0 (
  echo Failed to configure kubectl
  exit /b 1
)
call kubectl cluster-info
if !ERRORLEVEL! neq 0 (
  echo kubectl cluster-info failed
  exit /b 1
)

echo.
echo === Creating Kubernetes Secret ===
call kubectl create secret generic cargo-tracker-secrets -n !NAMESPACE! --from-literal=db-user="!DB_USER_VALUE!" --from-literal=db-password="!DB_PASSWORD_VALUE!" --dry-run=client -o yaml | kubectl apply -f -
if !ERRORLEVEL! neq 0 (
  echo Failed to create Kubernetes secret
  exit /b 1
)

echo.
echo === Applying namespace ===
call kubectl apply -f kubernetes\namespace.yaml
if !ERRORLEVEL! neq 0 (
  echo Failed to apply namespace
  exit /b 1
)

echo.
echo === Replacing placeholders and applying deployment ===
REM Create a temporary copy with placeholders replaced
copy kubernetes\deployment.yaml /tmp\deployment.yaml.bak >nul

powershell -command "(gc /tmp/deployment.yaml.bak) -replace '{{IMAGE_URI}}', '!IMAGE_URI!' | Out-File -encoding UTF8 /tmp/deployment.yaml.bak"
powershell -command "(gc /tmp/deployment.yaml.bak) -replace '{{DB_URL}}', '!DB_URL_VALUE!' | Out-File -encoding UTF8 /tmp/deployment.yaml.bak"
powershell -command "(gc /tmp/deployment.yaml.bak) -replace '{{GRAPH_TRAVERSAL_URL}}', '!GRAPH_TRAVERSAL_URL_VALUE!' | Out-File -encoding UTF8 /tmp/deployment.yaml.bak"

call kubectl apply -f /tmp/deployment.yaml.bak
if !ERRORLEVEL! neq 0 (
  echo Failed to apply deployment
  exit /b 1
)
del /q /tmp\deployment.yaml.bak

echo.
echo === Applying service and ingress ===
call kubectl apply -f kubernetes\service.yaml
if !ERRORLEVEL! neq 0 (
  echo Failed to apply service
  exit /b 1
)
call kubectl apply -f kubernetes\ingress.yaml
if !ERRORLEVEL! neq 0 (
  echo Failed to apply ingress
  exit /b 1
)

echo.
echo === Waiting for deployment rollout ===
call kubectl rollout status deployment/cargo-tracker -n !NAMESPACE!
if !ERRORLEVEL! neq 0 (
  echo Deployment rollout failed
  exit /b 1
)

echo.
echo === Deployment status ===
call kubectl get pods,svc,ingress -n !NAMESPACE!

echo.
echo === Application URL ===
call kubectl get ingress cargo-tracker-ingress -n !NAMESPACE! -o jsonpath={.status.loadBalancer.ingress[0].hostname}

echo.
echo === Deployment complete ===
echo.
echo Rollback instructions:
echo   kubectl rollout undo deployment/cargo-tracker -n !NAMESPACE!
echo.
echo To troubleshoot:
echo   kubectl describe pods -n !NAMESPACE!
echo   kubectl logs -l app=cargo-tracker -n !NAMESPACE!

exit /b 0

:read_password
set /p %1="%~2"
exit /b 0
