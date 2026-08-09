@echo off
setlocal enabledelayedexpansion

set "APP_NAME=cargo-tracker"
set "NAMESPACE=cargo-tracker"
set "MANIFEST_DIR=kubernetes"
set "DEPLOYMENT_FILE=kubernetes\deployment.yaml"

echo Azure AKS deployment for !APP_NAME!
set /p RESOURCE_GROUP="Enter Azure resource group: "
if "!RESOURCE_GROUP!"=="" (echo Azure resource group is required & exit /b 1)
set /p CLUSTER_NAME="Enter AKS cluster name: "
if "!CLUSTER_NAME!"=="" (echo AKS cluster name is required & exit /b 1)
set /p IMAGE_URI="Enter Docker image URI with tag: "
if "!IMAGE_URI!"=="" (echo Docker image URI is required & exit /b 1)

set /p POSTGRESQL_JDBC_URL="Enter value for POSTGRESQL_JDBC_URL or press Enter to skip: "
set /p POSTGRESQL_USERNAME="Enter value for POSTGRESQL_USERNAME or press Enter to skip: "
set /p POSTGRESQL_PASSWORD="Enter value for POSTGRESQL_PASSWORD or press Enter to skip: "
set /p GRAPH_TRAVERSAL_URL="Enter value for GRAPH_TRAVERSAL_URL or press Enter to skip: "
if "!POSTGRESQL_JDBC_URL!"=="" set "POSTGRESQL_JDBC_URL=jdbc:postgresql://postgres.example.com:5432/postgres"
if "!POSTGRESQL_USERNAME!"=="" set "POSTGRESQL_USERNAME=cargotracker"
if "!POSTGRESQL_PASSWORD!"=="" set "POSTGRESQL_PASSWORD=cargotracker"
if "!GRAPH_TRAVERSAL_URL!"=="" set "GRAPH_TRAVERSAL_URL=http://cargo-tracker-service/rest/graph-traversal/shortest-path"

echo Configuring kubectl for AKS cluster
az aks get-credentials --resource-group !RESOURCE_GROUP! --name !CLUSTER_NAME! --overwrite-existing
if %ERRORLEVEL% neq 0 (echo Failed to configure AKS credentials & exit /b 1)

echo Verifying Kubernetes cluster connectivity
kubectl cluster-info >nul
if %ERRORLEVEL% neq 0 (echo Kubernetes cluster connectivity check failed & exit /b 1)

echo Replacing deployment placeholders
powershell -NoProfile -Command "(Get-Content '!DEPLOYMENT_FILE!' -Raw).Replace('{{IMAGE_URI}}','!IMAGE_URI!').Replace('{{POSTGRESQL_JDBC_URL}}','!POSTGRESQL_JDBC_URL!').Replace('{{POSTGRESQL_USERNAME}}','!POSTGRESQL_USERNAME!').Replace('{{POSTGRESQL_PASSWORD}}','!POSTGRESQL_PASSWORD!').Replace('{{GRAPH_TRAVERSAL_URL}}','!GRAPH_TRAVERSAL_URL!') | Set-Content '!DEPLOYMENT_FILE!'"
if %ERRORLEVEL% neq 0 (echo Failed to replace manifest placeholders & exit /b 1)

echo Applying Kubernetes manifests
kubectl apply -f !MANIFEST_DIR!\namespace.yaml
if %ERRORLEVEL% neq 0 (echo Failed to apply namespace & exit /b 1)
kubectl apply -f !MANIFEST_DIR!\deployment.yaml
if %ERRORLEVEL% neq 0 (echo Failed to apply deployment & exit /b 1)
kubectl apply -f !MANIFEST_DIR!\service.yaml
if %ERRORLEVEL% neq 0 (echo Failed to apply service & exit /b 1)
kubectl apply -f !MANIFEST_DIR!\ingress.yaml
if %ERRORLEVEL% neq 0 (echo Failed to apply ingress & exit /b 1)

echo Waiting for rollout to complete
kubectl rollout status deployment/!APP_NAME! -n !NAMESPACE! --timeout=300s
if %ERRORLEVEL% neq 0 (
  echo Deployment rollout failed
  kubectl get pods -n !NAMESPACE!
  echo Rollback command kubectl rollout undo deployment/!APP_NAME! -n !NAMESPACE!
  exit /b 1
)

echo Deployment resources
kubectl get pods,svc,ingress -n !NAMESPACE!

echo Ingress host
kubectl get ingress !APP_NAME!-ingress -n !NAMESPACE! -o jsonpath="{.spec.rules[0].host}"
echo.

echo Deployment completed successfully
endlocal
