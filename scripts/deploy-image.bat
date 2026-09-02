@echo off
setlocal enabledelayedexpansion

rem ============================================================
rem deploy-image.bat  -  Deploy cargo-tracker to AWS EKS
rem ============================================================

set APP_NAME=cargo-tracker
set NAMESPACE=cargo-tracker
set K8S_DIR=kubernetes

echo ==============================================
echo   cargo-tracker  -  Deploy to AWS EKS
echo ==============================================

rem -------------------------------------------------------
rem Collect deployment parameters
rem -------------------------------------------------------
set /p AWS_REGION="Enter AWS region [us-east-1]: "
if "!AWS_REGION!"=="" set AWS_REGION=us-east-1

set /p CLUSTER_NAME="Enter EKS cluster name: "
if "!CLUSTER_NAME!"=="" (
    echo ERROR: EKS cluster name is required.
    exit /b 1
)

set /p IMAGE_URI="Enter full Docker image URI (e.g. 123456789.dkr.ecr.us-east-1.amazonaws.com/cargo-tracker:latest): "
if "!IMAGE_URI!"=="" (
    echo ERROR: Docker image URI is required.
    exit /b 1
)

echo.
echo --- Optional environment variable configuration ---
echo (Press Enter to skip any variable)

set /p REDIS_HOST="Enter REDIS_HOST (ElastiCache endpoint) [localhost]: "
if "!REDIS_HOST!"=="" set REDIS_HOST=localhost

set /p REDIS_PORT="Enter REDIS_PORT [6379]: "
if "!REDIS_PORT!"=="" set REDIS_PORT=6379

set /p REDIS_PASSWORD="Enter REDIS_PASSWORD (leave blank if none): "

set /p DB_JDBC_URL="Enter DB_JDBC_URL [jdbc:h2:file:./cargo-tracker-data/cargo-tracker-database]: "
if "!DB_JDBC_URL!"=="" set DB_JDBC_URL=jdbc:h2:file:./cargo-tracker-data/cargo-tracker-database

set /p DB_USER="Enter DB_USER: "

set /p DB_PASSWORD="Enter DB_PASSWORD: "

set /p GRAPH_TRAVERSAL_URL="Enter GRAPH_TRAVERSAL_URL [http://localhost:8080/cargo-tracker/rest/graph-traversal/shortest-path]: "
if "!GRAPH_TRAVERSAL_URL!"=="" set GRAPH_TRAVERSAL_URL=http://localhost:8080/cargo-tracker/rest/graph-traversal/shortest-path

rem -------------------------------------------------------
rem Configure kubectl for EKS
rem -------------------------------------------------------
echo.
echo Configuring kubectl for EKS cluster: !CLUSTER_NAME! ...
aws eks update-kubeconfig --region !AWS_REGION! --name !CLUSTER_NAME!
if !ERRORLEVEL! neq 0 (
    echo ERROR: Failed to configure kubectl.
    exit /b 1
)

echo Verifying cluster connectivity...
kubectl cluster-info
if !ERRORLEVEL! neq 0 (
    echo ERROR: Cannot connect to EKS cluster.
    exit /b 1
)

rem -------------------------------------------------------
rem Update Kubernetes manifests using PowerShell
rem -------------------------------------------------------
echo.
echo Updating Kubernetes manifests...

set TMP_DIR=%TEMP%\cargo-tracker-deploy-%RANDOM%
mkdir "!TMP_DIR!"
xcopy /E /I /Q "%K8S_DIR%\*" "!TMP_DIR!\" >nul

powershell -NoProfile -Command ^
  "(Get-Content '!TMP_DIR!\deployment.yaml') ^
    -replace '{{IMAGE_URI}}','!IMAGE_URI!' ^
    -replace '{{REDIS_HOST}}','!REDIS_HOST!' ^
    -replace '{{REDIS_PORT}}','!REDIS_PORT!' ^
    -replace '{{REDIS_PASSWORD}}','!REDIS_PASSWORD!' ^
    -replace '{{DB_JDBC_URL}}','!DB_JDBC_URL!' ^
    -replace '{{DB_USER}}','!DB_USER!' ^
    -replace '{{DB_PASSWORD}}','!DB_PASSWORD!' ^
    -replace '{{GRAPH_TRAVERSAL_URL}}','!GRAPH_TRAVERSAL_URL!' ^
  | Set-Content '!TMP_DIR!\deployment.yaml'"
if !ERRORLEVEL! neq 0 (
    echo ERROR: Failed to update deployment manifest.
    exit /b 1
)

rem -------------------------------------------------------
rem Apply manifests in order
rem -------------------------------------------------------
echo.
echo Applying Kubernetes manifests...

echo   [1/4] Applying namespace...
kubectl apply -f "!TMP_DIR!\namespace.yaml"
if !ERRORLEVEL! neq 0 (
    echo ERROR: Failed to apply namespace.
    exit /b 1
)

echo   [2/4] Applying deployment...
kubectl apply -f "!TMP_DIR!\deployment.yaml"
if !ERRORLEVEL! neq 0 (
    echo ERROR: Failed to apply deployment.
    exit /b 1
)

echo   [3/4] Applying service...
kubectl apply -f "!TMP_DIR!\service.yaml"
if !ERRORLEVEL! neq 0 (
    echo ERROR: Failed to apply service.
    exit /b 1
)

echo   [4/4] Applying ingress...
kubectl apply -f "!TMP_DIR!\ingress.yaml"
if !ERRORLEVEL! neq 0 (
    echo ERROR: Failed to apply ingress.
    exit /b 1
)

rem -------------------------------------------------------
rem Wait for rollout
rem -------------------------------------------------------
echo.
echo Waiting for deployment rollout...
kubectl rollout status deployment/!APP_NAME! -n !NAMESPACE! --timeout=300s
if !ERRORLEVEL! neq 0 (
    echo ERROR: Deployment rollout failed.
    echo Run: kubectl rollout undo deployment/!APP_NAME! -n !NAMESPACE!
    exit /b 1
)

rem -------------------------------------------------------
rem Verify resources
rem -------------------------------------------------------
echo.
echo Verifying deployed resources...
kubectl get pods,svc,ingress -n !NAMESPACE!

rem -------------------------------------------------------
rem Cleanup temp files
rem -------------------------------------------------------
rmdir /S /Q "!TMP_DIR!" >nul 2>&1

echo.
echo ==============================================
echo   Deployment complete!
echo   Namespace : !NAMESPACE!
echo   Image     : !IMAGE_URI!
echo ==============================================
echo.
echo Rollback command (if needed):
echo   kubectl rollout undo deployment/!APP_NAME! -n !NAMESPACE!

endlocal
