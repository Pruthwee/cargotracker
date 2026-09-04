@echo off
setlocal enabledelayedexpansion

echo ============================================
echo   Cargo Tracker - Deploy to AWS EKS
echo ============================================
echo.

REM Prompt for AWS region
set /p "AWS_REGION=Enter AWS Region (e.g. us-east-1): "
if "!AWS_REGION!"=="" (
    echo ERROR: AWS Region is required.
    exit /b 1
)

REM Prompt for EKS cluster name
set /p "CLUSTER_NAME=Enter EKS Cluster Name: "
if "!CLUSTER_NAME!"=="" (
    echo ERROR: EKS Cluster Name is required.
    exit /b 1
)

REM Prompt for Docker image URI
set /p "IMAGE_URI=Enter full Docker image URI (e.g. 123456789.dkr.ecr.us-east-1.amazonaws.com/cargo-tracker:latest): "
if "!IMAGE_URI!"=="" (
    echo ERROR: Docker image URI is required.
    exit /b 1
)

echo.
echo --- Application Configuration ---
echo The following environment variables are used by cargo-tracker.
echo Press Enter to use the default placeholder value.
echo.

set /p "DB_JDBC_URL=Enter DB_JDBC_URL (PostgreSQL JDBC URL): "
if "!DB_JDBC_URL!"=="" set "DB_JDBC_URL=jdbc:postgresql://localhost:5432/postgres"

set /p "DB_USER=Enter DB_USER (PostgreSQL username): "
if "!DB_USER!"=="" set "DB_USER=postgres"

set /p "DB_PASSWORD=Enter DB_PASSWORD (PostgreSQL password): "
if "!DB_PASSWORD!"=="" set "DB_PASSWORD=postgres"

set /p "REDIS_HOST=Enter REDIS_HOST (Redis/ElastiCache host): "
if "!REDIS_HOST!"=="" set "REDIS_HOST=localhost"

set /p "REDIS_PORT=Enter REDIS_PORT (Redis port, default 6379): "
if "!REDIS_PORT!"=="" set "REDIS_PORT=6379"

set /p "GRAPH_TRAVERSAL_URL=Enter GRAPH_TRAVERSAL_URL (default: http://localhost:8080/rest/graph-traversal/shortest-path): "
if "!GRAPH_TRAVERSAL_URL!"=="" set "GRAPH_TRAVERSAL_URL=http://localhost:8080/rest/graph-traversal/shortest-path"

echo.
echo --- Configuring kubectl for EKS ---
aws eks update-kubeconfig --region !AWS_REGION! --name !CLUSTER_NAME!
if !ERRORLEVEL! neq 0 (
    echo ERROR: Failed to configure kubectl for EKS cluster.
    exit /b 1
)

echo.
echo --- Verifying cluster connectivity ---
kubectl cluster-info
if !ERRORLEVEL! neq 0 (
    echo ERROR: Cannot connect to EKS cluster.
    exit /b 1
)

echo.
echo --- Updating Kubernetes manifests ---

REM Copy manifests to temp directory
copy /Y kubernetes\deployment.yaml %TEMP%\cargo-tracker-deployment.yaml >nul
copy /Y kubernetes\service.yaml    %TEMP%\cargo-tracker-service.yaml    >nul
copy /Y kubernetes\ingress.yaml    %TEMP%\cargo-tracker-ingress.yaml    >nul
copy /Y kubernetes\namespace.yaml  %TEMP%\cargo-tracker-namespace.yaml  >nul

REM Replace placeholders using PowerShell
powershell -Command "(Get-Content '%TEMP%\cargo-tracker-deployment.yaml') -replace '{{IMAGE_URI}}','!IMAGE_URI!' -replace '{{DB_JDBC_URL}}','!DB_JDBC_URL!' -replace '{{DB_USER}}','!DB_USER!' -replace '{{DB_PASSWORD}}','!DB_PASSWORD!' -replace '{{REDIS_HOST}}','!REDIS_HOST!' -replace '{{REDIS_PORT}}','!REDIS_PORT!' -replace '{{GRAPH_TRAVERSAL_URL}}','!GRAPH_TRAVERSAL_URL!' | Set-Content '%TEMP%\cargo-tracker-deployment.yaml'"
if !ERRORLEVEL! neq 0 (
    echo ERROR: Failed to update deployment manifest.
    exit /b 1
)

echo.
echo --- Applying Kubernetes manifests ---

echo Applying namespace...
kubectl apply -f %TEMP%\cargo-tracker-namespace.yaml
if !ERRORLEVEL! neq 0 (
    echo ERROR: Failed to apply namespace.
    exit /b 1
)

echo Applying deployment...
kubectl apply -f %TEMP%\cargo-tracker-deployment.yaml
if !ERRORLEVEL! neq 0 (
    echo ERROR: Failed to apply deployment.
    exit /b 1
)

echo Applying service...
kubectl apply -f %TEMP%\cargo-tracker-service.yaml
if !ERRORLEVEL! neq 0 (
    echo ERROR: Failed to apply service.
    exit /b 1
)

echo Applying ingress...
kubectl apply -f %TEMP%\cargo-tracker-ingress.yaml
if !ERRORLEVEL! neq 0 (
    echo ERROR: Failed to apply ingress.
    exit /b 1
)

echo.
echo --- Waiting for deployment rollout ---
kubectl rollout status deployment/cargo-tracker -n cargo-tracker --timeout=300s
if !ERRORLEVEL! neq 0 (
    echo.
    echo ERROR: Deployment rollout failed. Initiating rollback...
    kubectl rollout undo deployment/cargo-tracker -n cargo-tracker
    echo Rollback initiated. Check pod logs with:
    echo   kubectl logs -l app=cargo-tracker -n cargo-tracker
    exit /b 1
)

echo.
echo --- Verifying deployed resources ---
kubectl get pods,svc,ingress -n cargo-tracker

echo.
echo ============================================
echo   SUCCESS: cargo-tracker deployed to EKS!
echo ============================================
echo.
echo Useful commands:
echo   kubectl get pods -n cargo-tracker
echo   kubectl logs -l app=cargo-tracker -n cargo-tracker
echo   kubectl describe deployment cargo-tracker -n cargo-tracker
echo   kubectl rollout undo deployment/cargo-tracker -n cargo-tracker

endlocal
