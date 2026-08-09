@echo off
setlocal enabledelayedexpansion

set "APP_NAME=cargo-tracker"
set "NAMESPACE=cargo-tracker"
set "MANIFEST_DIR=kubernetes"
set "WORK_MANIFEST_DIR=%TEMP%\cargo-tracker-k8s-%RANDOM%"

echo Cargo Tracker Azure AKS deployment
set /p RESOURCE_GROUP="Enter Azure resource group: "
set /p CLUSTER_NAME="Enter AKS cluster name: "
set /p IMAGE_URI="Enter Docker image URI registry/repository:tag: "
set /p POSTGRESQL_JDBC_URL="Enter PostgreSQL JDBC URL or press Enter to skip: "
set /p POSTGRESQL_USERNAME="Enter PostgreSQL username or press Enter to skip: "
set /p POSTGRESQL_PASSWORD="Enter PostgreSQL password or press Enter to skip secret creation: "
set /p GRAPH_TRAVERSAL_URL="Enter Graph Traversal URL or press Enter for default: "

if "%RESOURCE_GROUP%"=="" (
  echo Resource group is required
  exit /b 1
)
if "%CLUSTER_NAME%"=="" (
  echo AKS cluster name is required
  exit /b 1
)
if "%IMAGE_URI%"=="" (
  echo Image URI is required
  exit /b 1
)
if "%POSTGRESQL_JDBC_URL%"=="" set "POSTGRESQL_JDBC_URL=jdbc:postgresql://postgres.default.svc.cluster.local:5432/postgres"
if "%POSTGRESQL_USERNAME%"=="" set "POSTGRESQL_USERNAME=postgres"
if "%GRAPH_TRAVERSAL_URL%"=="" set "GRAPH_TRAVERSAL_URL=http://localhost:8080/rest/graph-traversal/shortest-path"

if exist "%WORK_MANIFEST_DIR%" rmdir /s /q "%WORK_MANIFEST_DIR%"
xcopy "%MANIFEST_DIR%" "%WORK_MANIFEST_DIR%" /E /I /Q >nul
if %ERRORLEVEL% neq 0 (echo Failed to prepare manifests & exit /b 1)

echo Preparing manifests
powershell -NoProfile -Command "(Get-Content '%WORK_MANIFEST_DIR%\deployment.yaml') -replace '\{\{IMAGE_URI\}\}', '%IMAGE_URI%' -replace '\{\{POSTGRESQL_JDBC_URL\}\}', '%POSTGRESQL_JDBC_URL%' -replace '\{\{POSTGRESQL_USERNAME\}\}', '%POSTGRESQL_USERNAME%' -replace '\{\{GRAPH_TRAVERSAL_URL\}\}', '%GRAPH_TRAVERSAL_URL%' | Set-Content '%WORK_MANIFEST_DIR%\deployment.yaml'"
if %ERRORLEVEL% neq 0 (echo Failed to update deployment manifest & exit /b 1)

echo Configuring kubectl for AKS cluster
az aks get-credentials --resource-group "%RESOURCE_GROUP%" --name "%CLUSTER_NAME%" --overwrite-existing
if %ERRORLEVEL% neq 0 (echo Failed to configure AKS credentials & exit /b 1)

echo Verifying cluster connectivity
kubectl cluster-info
if %ERRORLEVEL% neq 0 (echo kubectl cluster connectivity failed & exit /b 1)

echo Applying namespace
kubectl apply -f "%WORK_MANIFEST_DIR%\namespace.yaml"
if %ERRORLEVEL% neq 0 (echo Failed to apply namespace & exit /b 1)

if not "%POSTGRESQL_PASSWORD%"=="" (
  echo Creating or updating application secret
  kubectl create secret generic cargo-tracker-secrets --namespace "%NAMESPACE%" --from-literal=postgresql-password="%POSTGRESQL_PASSWORD%" --dry-run=client -o yaml > "%WORK_MANIFEST_DIR%\secret.yaml"
  if %ERRORLEVEL% neq 0 (echo Failed to create secret manifest & exit /b 1)
  kubectl apply -f "%WORK_MANIFEST_DIR%\secret.yaml"
  if %ERRORLEVEL% neq 0 (echo Failed to apply secret & exit /b 1)
) else (
  echo PostgreSQL password skipped. Ensure secret cargo-tracker-secrets with key postgresql-password exists if required.
)

echo Applying deployment service and ingress
kubectl apply -f "%WORK_MANIFEST_DIR%\deployment.yaml"
if %ERRORLEVEL% neq 0 (echo Failed to apply deployment & exit /b 1)
kubectl apply -f "%WORK_MANIFEST_DIR%\service.yaml"
if %ERRORLEVEL% neq 0 (echo Failed to apply service & exit /b 1)
kubectl apply -f "%WORK_MANIFEST_DIR%\ingress.yaml"
if %ERRORLEVEL% neq 0 (echo Failed to apply ingress & exit /b 1)

echo Waiting for rollout
kubectl rollout status deployment/%APP_NAME% -n %NAMESPACE% --timeout=300s
if %ERRORLEVEL% neq 0 (echo Rollout failed. Rollback command kubectl rollout undo deployment/%APP_NAME% -n %NAMESPACE% & exit /b 1)

echo Deployment resources
kubectl get pods,svc,ingress -n %NAMESPACE%

echo Application URL
kubectl get ingress cargo-tracker-ingress -n %NAMESPACE% -o jsonpath="{.spec.rules[0].host}"
echo.
echo Rollback if needed kubectl rollout undo deployment/%APP_NAME% -n %NAMESPACE%

rmdir /s /q "%WORK_MANIFEST_DIR%" >nul 2>nul
endlocal
