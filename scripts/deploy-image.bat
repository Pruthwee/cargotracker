@echo off
setlocal enabledelayedexpansion

echo ============================================
echo   Cargo Tracker - Deploy to AWS EKS
echo ============================================
echo.

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
echo --- Application Configuration ---
echo Press Enter to skip any optional value.
echo.

set /p POSTGRES_JDBC_URL="Enter PostgreSQL JDBC URL (e.g. jdbc:postgresql://host:5432/cargotracker): "
set /p POSTGRES_USERNAME="Enter PostgreSQL Username: "
set /p POSTGRES_PASSWORD="Enter PostgreSQL Password: "
set /p GRAPH_TRAVERSAL_URL_INPUT="Enter Graph Traversal URL (default: http://localhost:8080/rest/graph-traversal/shortest-path): "
if "!GRAPH_TRAVERSAL_URL_INPUT!"=="" (
    set GRAPH_TRAVERSAL_URL=http://localhost:8080/rest/graph-traversal/shortest-path
) else (
    set GRAPH_TRAVERSAL_URL=!GRAPH_TRAVERSAL_URL_INPUT!
)
set /p REDIS_HOST="Enter Redis Host (e.g. my-redis.cache.amazonaws.com): "
set /p REDIS_PORT_INPUT="Enter Redis Port (default: 6379): "
if "!REDIS_PORT_INPUT!"=="" (
    set REDIS_PORT=6379
) else (
    set REDIS_PORT=!REDIS_PORT_INPUT!
)

echo.
echo --- Configuring kubectl for EKS ---
aws eks update-kubeconfig --region !AWS_REGION! --name !CLUSTER_NAME!
if !ERRORLEVEL! neq 0 (
    echo ERROR: Failed to configure kubectl for EKS cluster.
    exit /b 1
)

echo Verifying cluster connectivity...
kubectl cluster-info
if !ERRORLEVEL! neq 0 (
    echo ERROR: Cannot connect to EKS cluster. Check your credentials and cluster name.
    exit /b 1
)

echo.
echo --- Updating Kubernetes manifests ---

powershell -Command "(Get-Content kubernetes\deployment.yaml) -replace '{{IMAGE_URI}}', '!IMAGE_URI!' | Set-Content kubernetes\deployment.yaml"

if not "!POSTGRES_JDBC_URL!"=="" (
    powershell -Command "(Get-Content kubernetes\deployment.yaml) -replace '{{POSTGRES_JDBC_URL}}', '!POSTGRES_JDBC_URL!' | Set-Content kubernetes\deployment.yaml"
)
if not "!POSTGRES_USERNAME!"=="" (
    powershell -Command "(Get-Content kubernetes\deployment.yaml) -replace '{{POSTGRES_USERNAME}}', '!POSTGRES_USERNAME!' | Set-Content kubernetes\deployment.yaml"
)
if not "!POSTGRES_PASSWORD!"=="" (
    powershell -Command "(Get-Content kubernetes\deployment.yaml) -replace '{{POSTGRES_PASSWORD}}', '!POSTGRES_PASSWORD!' | Set-Content kubernetes\deployment.yaml"
)
powershell -Command "(Get-Content kubernetes\deployment.yaml) -replace '{{GRAPH_TRAVERSAL_URL}}', '!GRAPH_TRAVERSAL_URL!' | Set-Content kubernetes\deployment.yaml"
if not "!REDIS_HOST!"=="" (
    powershell -Command "(Get-Content kubernetes\deployment.yaml) -replace '{{REDIS_HOST}}', '!REDIS_HOST!' | Set-Content kubernetes\deployment.yaml"
)
powershell -Command "(Get-Content kubernetes\deployment.yaml) -replace '{{REDIS_PORT}}', '!REDIS_PORT!' | Set-Content kubernetes\deployment.yaml"

echo.
echo --- Applying Kubernetes manifests ---

echo Applying namespace...
kubectl apply -f kubernetes\namespace.yaml
if !ERRORLEVEL! neq 0 ( echo ERROR: Failed to apply namespace & exit /b 1 )

echo Applying deployment...
kubectl apply -f kubernetes\deployment.yaml
if !ERRORLEVEL! neq 0 ( echo ERROR: Failed to apply deployment & exit /b 1 )

echo Applying service...
kubectl apply -f kubernetes\service.yaml
if !ERRORLEVEL! neq 0 ( echo ERROR: Failed to apply service & exit /b 1 )

echo Applying ingress...
kubectl apply -f kubernetes\ingress.yaml
if !ERRORLEVEL! neq 0 ( echo ERROR: Failed to apply ingress & exit /b 1 )

echo.
echo --- Waiting for deployment rollout ---
kubectl rollout status deployment/cargo-tracker -n cargo-tracker --timeout=300s
if !ERRORLEVEL! neq 0 (
    echo WARNING: Deployment rollout did not complete within timeout.
    echo Check pod status: kubectl get pods -n cargo-tracker
)

echo.
echo --- Verifying deployed resources ---
kubectl get pods,svc,ingress -n cargo-tracker

echo.
echo ============================================
echo   Deployment Complete!
echo ============================================
echo.
echo Rollback command (if needed):
echo   kubectl rollout undo deployment/cargo-tracker -n cargo-tracker

endlocal
