@echo off
setlocal enabledelayedexpansion

REM ============================================================
REM deploy-image.bat - Deploy cargo-tracker to AWS EKS
REM ============================================================

set APP_NAME=cargo-tracker
set NAMESPACE=cargo-tracker

echo ============================================
echo   cargo-tracker - Deploy to AWS EKS
echo ============================================
echo.

REM Prompt for AWS region
set /p AWS_REGION="Enter AWS Region (e.g., us-east-1): "
if "!AWS_REGION!"=="" (
    echo ERROR: AWS Region is required.
    exit /b 1
)

REM Prompt for EKS cluster name
set /p CLUSTER_NAME="Enter EKS Cluster Name: "
if "!CLUSTER_NAME!"=="" (
    echo ERROR: EKS Cluster Name is required.
    exit /b 1
)

REM Prompt for Docker image URI
set /p IMAGE_URI="Enter full Docker image URI (e.g., 123456789.dkr.ecr.us-east-1.amazonaws.com/cargo-tracker:latest): "
if "!IMAGE_URI!"=="" (
    echo ERROR: Docker image URI is required.
    exit /b 1
)

echo.
echo --- Optional: Application Configuration ---
echo Press Enter to skip any value
echo.

set /p DB_JDBC_URL="Enter DB_JDBC_URL (e.g., jdbc:postgresql://host:5432/cargotracker): "
set /p DB_DRIVER_CLASS="Enter DB_DRIVER_CLASS (e.g., org.postgresql.ds.PGPoolingDataSource): "
set /p DB_USER="Enter DB_USER: "
set /p DB_PASSWORD="Enter DB_PASSWORD: "
set /p GRAPH_TRAVERSAL_URL="Enter GRAPH_TRAVERSAL_URL: "

echo.
echo --------------------------------------------
echo Configuring kubectl for EKS cluster: !CLUSTER_NAME!
aws eks update-kubeconfig --region !AWS_REGION! --name !CLUSTER_NAME!
if !ERRORLEVEL! neq 0 (
    echo ERROR: Failed to configure kubectl for EKS cluster.
    exit /b 1
)

echo Verifying cluster connectivity...
kubectl cluster-info
if !ERRORLEVEL! neq 0 (
    echo ERROR: Cannot connect to Kubernetes cluster.
    exit /b 1
)

echo.
echo --------------------------------------------
echo Updating Kubernetes manifests...

REM Create temp directory for modified manifests
set TEMP_DIR=%TEMP%\cargo-tracker-deploy-%RANDOM%
mkdir !TEMP_DIR!
copy kubernetes\namespace.yaml !TEMP_DIR!\namespace.yaml >nul
copy kubernetes\deployment.yaml !TEMP_DIR!\deployment.yaml >nul
copy kubernetes\service.yaml !TEMP_DIR!\service.yaml >nul
copy kubernetes\ingress.yaml !TEMP_DIR!\ingress.yaml >nul

REM Replace IMAGE_URI placeholder using PowerShell
powershell -Command "(Get-Content '!TEMP_DIR!\deployment.yaml') -replace '{{IMAGE_URI}}', '!IMAGE_URI!' | Set-Content '!TEMP_DIR!\deployment.yaml'"

REM Replace DB_JDBC_URL
if "!DB_JDBC_URL!"=="" set DB_JDBC_URL=jdbc:h2:file:./cargo-tracker-data/cargo-tracker-database
powershell -Command "(Get-Content '!TEMP_DIR!\deployment.yaml') -replace '{{DB_JDBC_URL}}', '!DB_JDBC_URL!' | Set-Content '!TEMP_DIR!\deployment.yaml'"

REM Replace DB_DRIVER_CLASS
if "!DB_DRIVER_CLASS!"=="" set DB_DRIVER_CLASS=org.h2.jdbcx.JdbcDataSource
powershell -Command "(Get-Content '!TEMP_DIR!\deployment.yaml') -replace '{{DB_DRIVER_CLASS}}', '!DB_DRIVER_CLASS!' | Set-Content '!TEMP_DIR!\deployment.yaml'"

REM Replace DB_USER
powershell -Command "(Get-Content '!TEMP_DIR!\deployment.yaml') -replace '{{DB_USER}}', '!DB_USER!' | Set-Content '!TEMP_DIR!\deployment.yaml'"

REM Replace DB_PASSWORD
powershell -Command "(Get-Content '!TEMP_DIR!\deployment.yaml') -replace '{{DB_PASSWORD}}', '!DB_PASSWORD!' | Set-Content '!TEMP_DIR!\deployment.yaml'"

REM Replace GRAPH_TRAVERSAL_URL
if "!GRAPH_TRAVERSAL_URL!"=="" set GRAPH_TRAVERSAL_URL=http://localhost:8080/cargo-tracker/rest/graph-traversal/shortest-path
powershell -Command "(Get-Content '!TEMP_DIR!\deployment.yaml') -replace '{{GRAPH_TRAVERSAL_URL}}', '!GRAPH_TRAVERSAL_URL!' | Set-Content '!TEMP_DIR!\deployment.yaml'"

echo.
echo --------------------------------------------
echo Applying Kubernetes manifests...

echo 1. Applying namespace...
kubectl apply -f !TEMP_DIR!\namespace.yaml
if !ERRORLEVEL! neq 0 (
    echo ERROR: Failed to apply namespace.
    exit /b 1
)

echo 2. Applying deployment...
kubectl apply -f !TEMP_DIR!\deployment.yaml
if !ERRORLEVEL! neq 0 (
    echo ERROR: Failed to apply deployment.
    exit /b 1
)

echo 3. Applying service...
kubectl apply -f !TEMP_DIR!\service.yaml
if !ERRORLEVEL! neq 0 (
    echo ERROR: Failed to apply service.
    exit /b 1
)

echo 4. Applying ingress...
kubectl apply -f !TEMP_DIR!\ingress.yaml
if !ERRORLEVEL! neq 0 (
    echo ERROR: Failed to apply ingress.
    exit /b 1
)

echo.
echo --------------------------------------------
echo Waiting for deployment rollout...
kubectl rollout status deployment/!APP_NAME! -n !NAMESPACE! --timeout=300s
if !ERRORLEVEL! neq 0 (
    echo ERROR: Deployment rollout failed. Rolling back...
    kubectl rollout undo deployment/!APP_NAME! -n !NAMESPACE!
    echo Rollback initiated. Check pod status with:
    echo   kubectl get pods -n !NAMESPACE!
    exit /b 1
)

echo.
echo --------------------------------------------
echo Verifying deployed resources...
kubectl get pods,svc,ingress -n !NAMESPACE!

echo.
echo ============================================
echo   SUCCESS: cargo-tracker deployed to EKS!
echo   Namespace: !NAMESPACE!
echo   Image: !IMAGE_URI!
echo ============================================
echo.
echo Useful commands:
echo   kubectl get pods -n !NAMESPACE!
echo   kubectl logs -f deployment/!APP_NAME! -n !NAMESPACE!
echo   kubectl describe deployment/!APP_NAME! -n !NAMESPACE!
echo   kubectl rollout undo deployment/!APP_NAME! -n !NAMESPACE!

REM Cleanup temp directory
rmdir /s /q !TEMP_DIR!

endlocal
