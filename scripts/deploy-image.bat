@echo off
setlocal enabledelayedexpansion

REM Deploy cargo-tracker image to AWS EKS
REM Usage: scripts\deploy-image.bat

set "PROJECT_NAME=cargo-tracker"

echo === Cargo Tracker Deployment to AWS EKS ===

set /p AWS_REGION="Enter AWS region (e.g., us-east-1): "
set /p CLUSTER_NAME="Enter EKS cluster name: "
set /p IMAGE_URI="Enter full image URI (e.g., 123456789.dkr.ecr.us-east-1.amazonaws.com/cargo-tracker:latest): "

echo.
echo === Configuring kubectl for EKS ===
aws eks update-kubeconfig --region !AWS_REGION! --name !CLUSTER_NAME!
if !ERRORLEVEL! neq 0 (
    echo ERROR: Failed to configure kubectl
    exit /b 1
)

echo.
echo === Checking cluster connectivity ===
kubectl cluster-info
if !ERRORLEVEL! neq 0 exit /b 1

echo.
echo === Enter environment variable values ===
echo (Press Enter to skip a variable, which will leave its placeholder in place)

set /p DB_JDBC_URL="Enter value for DB_JDBC_URL (or press Enter to skip): "
set /p DB_USER="Enter value for DB_USER (or press Enter to skip): "
set /p GRAPH_TRAVERSAL_URL="Enter value for GRAPH_TRAVERSAL_URL (or press Enter to skip): "

set /p DB_PASSWORD="Enter value for DB_PASSWORD (or press Enter to skip): "

echo.
echo === Creating Kubernetes Secret ===
if not "!DB_PASSWORD!"=="" (
    kubectl create secret generic cargo-tracker-secrets -n cargo-tracker --from-literal=db-password="!DB_PASSWORD!" --dry-run=client -o yaml | kubectl apply -f -
    if !ERRORLEVEL! neq 0 (
        echo ERROR: Failed to create secret
        exit /b 1
    )
    echo Secret created successfully.
) else (
    echo No credentials provided, skipping secret creation.
)

echo.
echo === Applying Kubernetes manifests ===

kubectl apply -f kubernetes/namespace.yaml
if !ERRORLEVEL! neq 0 (
    echo ERROR: Failed to apply namespace
    exit /b 1
)

REM Apply deployment with placeholder substitution
powershell -Command "(gc kubernetes/deployment.yaml) -replace '{{IMAGE_URI}}', '!IMAGE_URI!' | Out-File -encoding ASCII kubernetes/deployment.yaml"
powershell -Command "(gc kubernetes/deployment.yaml) -replace '{{DB_JDBC_URL}}', '!DB_JDBC_URL!' | Out-File -encoding ASCII kubernetes/deployment.yaml"
powershell -Command "(gc kubernetes/deployment.yaml) -replace '{{DB_USER}}', '!DB_USER!' | Out-File -encoding ASCII kubernetes/deployment.yaml"
powershell -Command "(gc kubernetes/deployment.yaml) -replace '{{GRAPH_TRAVERSAL_URL}}', '!GRAPH_TRAVERSAL_URL!' | Out-File -encoding ASCII kubernetes/deployment.yaml"

kubectl apply -f kubernetes/deployment.yaml
if !ERRORLEVEL! neq 0 (
    echo ERROR: Failed to apply deployment
    exit /b 1
)

kubectl apply -f kubernetes/service.yaml
if !ERRORLEVEL! neq 0 (
    echo ERROR: Failed to apply service
    exit /b 1
)

kubectl apply -f kubernetes/ingress.yaml
if !ERRORLEVEL! neq 0 (
    echo ERROR: Failed to apply ingress
    exit /b 1
)

echo.
echo === Waiting for deployment rollout ===
kubectl rollout status deployment/cargo-tracker -n cargo-tracker
if !ERRORLEVEL! neq 0 (
    echo ERROR: Deployment rollout failed
    echo Rollback instructions:
    echo   kubectl rollout undo deployment/cargo-tracker -n cargo-tracker
    exit /b 1
)

echo.
echo === Deployment Details ===
kubectl get pods,svc,ingress -n cargo-tracker

echo.
echo === Application URL ===
echo http://cargo-tracker.example.com

echo.
echo === Deployment Complete ===
echo If you need to rollback:
echo   kubectl rollout undo deployment/cargo-tracker -n cargo-tracker

endlocal
