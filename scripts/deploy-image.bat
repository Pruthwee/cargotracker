@echo off
setlocal enabledelayedexpansion

set "APP_NAME=cargo-tracker"
set "NAMESPACE=cargo-tracker"
set "MANIFEST_DIR=kubernetes"
set "TMP_DIR=%TEMP%\cargo-tracker-k8s-%RANDOM%"

set /p RESOURCE_GROUP="Enter Azure resource group: "
if "%RESOURCE_GROUP%"=="" (
  echo Azure resource group is required
  exit /b 1
)

set /p CLUSTER_NAME="Enter AKS cluster name: "
if "%CLUSTER_NAME%"=="" (
  echo AKS cluster name is required
  exit /b 1
)

set /p IMAGE_URI="Enter full Docker image URI with tag: "
if "%IMAGE_URI%"=="" (
  echo Docker image URI is required
  exit /b 1
)

set /p POSTGRESQL_JDBC_URL="Enter value for POSTGRESQL_JDBC_URL or press Enter to skip: "
if "%POSTGRESQL_JDBC_URL%"=="" set "POSTGRESQL_JDBC_URL=jdbc:postgresql://postgresql.example.com:5432/postgres"
set /p POSTGRESQL_USERNAME="Enter value for POSTGRESQL_USERNAME or press Enter to skip: "
if "%POSTGRESQL_USERNAME%"=="" set "POSTGRESQL_USERNAME=postgres"
set /p POSTGRESQL_PASSWORD="Enter value for POSTGRESQL_PASSWORD or press Enter to skip: "
if "%POSTGRESQL_PASSWORD%"=="" set "POSTGRESQL_PASSWORD=postgres"

if exist "%TMP_DIR%" rmdir /s /q "%TMP_DIR%"
mkdir "%TMP_DIR%"
xcopy /E /I /Y "%MANIFEST_DIR%" "%TMP_DIR%\%MANIFEST_DIR%" >nul

powershell -NoProfile -Command "(Get-Content '%TMP_DIR%\%MANIFEST_DIR%\deployment.yaml') -replace '\{\{IMAGE_URI\}\}', '%IMAGE_URI%' -replace '\{\{POSTGRESQL_JDBC_URL\}\}', '%POSTGRESQL_JDBC_URL%' -replace '\{\{POSTGRESQL_USERNAME\}\}', '%POSTGRESQL_USERNAME%' -replace '\{\{POSTGRESQL_PASSWORD\}\}', '%POSTGRESQL_PASSWORD%' | Set-Content '%TMP_DIR%\%MANIFEST_DIR%\deployment.yaml'"
if %ERRORLEVEL% neq 0 (
  echo Failed to update manifests
  exit /b 1
)

echo Configuring kubectl for AKS cluster
az aks get-credentials --resource-group "%RESOURCE_GROUP%" --name "%CLUSTER_NAME%" --overwrite-existing
if %ERRORLEVEL% neq 0 (
  echo Failed to configure AKS credentials
  exit /b 1
)

echo Verifying Kubernetes cluster connectivity
kubectl cluster-info
if %ERRORLEVEL% neq 0 (
  echo Kubernetes connectivity verification failed
  exit /b 1
)

echo Applying Kubernetes manifests
kubectl apply -f "%TMP_DIR%\%MANIFEST_DIR%\namespace.yaml"
if %ERRORLEVEL% neq 0 exit /b 1
kubectl apply -f "%TMP_DIR%\%MANIFEST_DIR%\deployment.yaml"
if %ERRORLEVEL% neq 0 exit /b 1
kubectl apply -f "%TMP_DIR%\%MANIFEST_DIR%\service.yaml"
if %ERRORLEVEL% neq 0 exit /b 1
kubectl apply -f "%TMP_DIR%\%MANIFEST_DIR%\ingress.yaml"
if %ERRORLEVEL% neq 0 exit /b 1

echo Waiting for deployment rollout
kubectl rollout status deployment/%APP_NAME% -n %NAMESPACE% --timeout=300s
if %ERRORLEVEL% neq 0 (
  echo Deployment rollout failed
  echo Inspect with kubectl describe deployment/%APP_NAME% -n %NAMESPACE%
  echo Rollback with kubectl rollout undo deployment/%APP_NAME% -n %NAMESPACE%
  exit /b 1
)

echo Deployment resources
kubectl get pods,svc,ingress -n %NAMESPACE%

echo Deployment completed successfully
rmdir /s /q "%TMP_DIR%"
endlocal
