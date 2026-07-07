@echo off
setlocal enabledelayedexpansion

:: ============================================================
:: deploy-image.bat - Deploy cargo-tracker to AWS EKS
:: ============================================================

set APP_NAME=cargo-tracker
set NAMESPACE=cargo-tracker
set MANIFESTS_DIR=kubernetes

echo ==============================================
echo   cargo-tracker - AWS EKS Deployment Script
echo ==============================================
echo.

:: ---- Collect deployment inputs ----
set /p AWS_REGION="Enter AWS Region (e.g. us-east-1): "
if "!AWS_REGION!"=="" (
  echo ERROR: AWS Region is required.
  exit /b 1
)

set /p CLUSTER_NAME="Enter EKS Cluster Name: "
if "!CLUSTER_NAME!"=="" (
  echo ERROR: EKS Cluster Name is required.
  exit /b 1
)

set /p IMAGE_URI="Enter full Docker image URI (e.g. 123456789.dkr.ecr.us-east-1.amazonaws.com/cargo-tracker:latest): "
if "!IMAGE_URI!"=="" (
  echo ERROR: Docker image URI is required.
  exit /b 1
)

echo.
echo ---- Application Configuration ----
set /p GRAPH_TRAVERSAL_URL_INPUT="Enter GRAPH_TRAVERSAL_URL (or press Enter for default): "
if "!GRAPH_TRAVERSAL_URL_INPUT!"=="" (
  set GRAPH_TRAVERSAL_URL=http://localhost:8080/cargo-tracker/rest/graph-traversal/shortest-path
) else (
  set GRAPH_TRAVERSAL_URL=!GRAPH_TRAVERSAL_URL_INPUT!
)

set /p DB_JDBC_URL_INPUT="Enter DB_JDBC_URL (or press Enter for embedded H2): "
if "!DB_JDBC_URL_INPUT!"=="" (
  set DB_JDBC_URL=jdbc:h2:file:./cargo-tracker-data/cargo-tracker-database
) else (
  set DB_JDBC_URL=!DB_JDBC_URL_INPUT!
)

set /p DB_USER="Enter DB_USER (or press Enter to skip): "
set /p DB_PASSWORD="Enter DB_PASSWORD (or press Enter to skip): "

echo.
echo Summary:
echo   AWS Region   : !AWS_REGION!
echo   EKS Cluster  : !CLUSTER_NAME!
echo   Image URI    : !IMAGE_URI!
echo   Namespace    : !NAMESPACE!
echo.

:: ---- Configure kubectl for EKS ----
echo Configuring kubectl for EKS cluster: !CLUSTER_NAME! ...
aws eks update-kubeconfig --region !AWS_REGION! --name !CLUSTER_NAME!
if !ERRORLEVEL! neq 0 (
  echo ERROR: Failed to configure kubectl for EKS.
  exit /b 1
)

echo Verifying cluster connectivity...
kubectl cluster-info
if !ERRORLEVEL! neq 0 (
  echo ERROR: Cannot connect to EKS cluster.
  exit /b 1
)

:: ---- Prepare manifests (replace placeholders using PowerShell) ----
echo.
echo Preparing Kubernetes manifests...

set DEPLOY_TMP_DIR=%TEMP%\cargo-tracker-deploy-%RANDOM%
mkdir "!DEPLOY_TMP_DIR!"
xcopy /E /I /Q "!MANIFESTS_DIR!" "!DEPLOY_TMP_DIR!" >nul

:: Replace placeholders in deployment.yaml using PowerShell
powershell -Command "(Get-Content '!DEPLOY_TMP_DIR!\deployment.yaml') -replace '{{IMAGE_URI}}','!IMAGE_URI!' -replace '{{GRAPH_TRAVERSAL_URL}}','!GRAPH_TRAVERSAL_URL!' -replace '{{DB_JDBC_URL}}','!DB_JDBC_URL!' -replace '{{DB_USER}}','!DB_USER!' -replace '{{NAMESPACE}}','!NAMESPACE!' | Set-Content '!DEPLOY_TMP_DIR!\deployment.yaml'"
if !ERRORLEVEL! neq 0 (
  echo ERROR: Failed to prepare deployment manifest.
  exit /b 1
)

echo Manifests prepared in: !DEPLOY_TMP_DIR!

:: ---- Apply manifests in order ----
echo.
echo Applying Kubernetes manifests...

echo [1/4] Applying namespace...
kubectl apply -f "!DEPLOY_TMP_DIR!\namespace.yaml"
if !ERRORLEVEL! neq 0 (
  echo ERROR: Failed to apply namespace.
  exit /b 1
)

echo [2/4] Applying deployment...
kubectl apply -f "!DEPLOY_TMP_DIR!\deployment.yaml"
if !ERRORLEVEL! neq 0 (
  echo ERROR: Failed to apply deployment.
  exit /b 1
)

echo [3/4] Applying service...
kubectl apply -f "!DEPLOY_TMP_DIR!\service.yaml"
if !ERRORLEVEL! neq 0 (
  echo ERROR: Failed to apply service.
  exit /b 1
)

echo [4/4] Applying ingress...
kubectl apply -f "!DEPLOY_TMP_DIR!\ingress.yaml"
if !ERRORLEVEL! neq 0 (
  echo ERROR: Failed to apply ingress.
  exit /b 1
)

:: ---- Wait for rollout ----
echo.
echo Waiting for deployment rollout...
kubectl rollout status deployment/!APP_NAME! -n !NAMESPACE! --timeout=300s
if !ERRORLEVEL! neq 0 (
  echo.
  echo ERROR: Deployment rollout timed out or failed.
  echo Run the following to investigate:
  echo   kubectl describe deployment/!APP_NAME! -n !NAMESPACE!
  echo   kubectl get pods -n !NAMESPACE!
  echo   kubectl logs -l app=!APP_NAME! -n !NAMESPACE! --tail=50
  echo.
  echo To rollback:
  echo   kubectl rollout undo deployment/!APP_NAME! -n !NAMESPACE!
  exit /b 1
)

:: ---- Verify resources ----
echo.
echo Verifying deployed resources...
kubectl get pods,svc,ingress -n !NAMESPACE!

:: ---- Display access URL ----
echo.
echo Fetching application URL from ingress...
for /f "delims=" %%i in ('kubectl get ingress !APP_NAME!-ingress -n !NAMESPACE! -o jsonpath^="{.status.loadBalancer.ingress[0].hostname}" 2^>nul') do set INGRESS_HOST=%%i

if not "!INGRESS_HOST!"=="" (
  echo.
  echo ==============================================
  echo   Deployment Successful!
  echo   Application URL: http://!INGRESS_HOST!
  echo ==============================================
) else (
  echo.
  echo ==============================================
  echo   Deployment Successful!
  echo   Ingress hostname is still provisioning.
  echo   Run: kubectl get ingress -n !NAMESPACE!
  echo ==============================================
)

:: ---- Cleanup temp dir ----
rmdir /S /Q "!DEPLOY_TMP_DIR!" >nul 2>&1

endlocal
