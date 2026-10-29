@echo off & setlocal enabledelayedexpansion
rem Deploys the pushed cargotrackerM-Mono image to AWS EKS with kubectl.
rem Run it from the repository root: scripts\deploy-image.bat

set "APP_NAME=cargotrackerm-mono"
set "NAMESPACE=cargotrackerm-mono"

where aws >nul 2>&1
if !ERRORLEVEL! neq 0 (echo ERROR the AWS CLI is not installed or not in PATH & exit /b 1)
where kubectl >nul 2>&1
if !ERRORLEVEL! neq 0 (echo ERROR kubectl is not installed or not in PATH & exit /b 1)
where powershell >nul 2>&1
if !ERRORLEVEL! neq 0 (echo ERROR PowerShell is required & exit /b 1)

echo === AWS EKS deployment of !APP_NAME! ===
set /p AWS_REGION="Enter the AWS region (for example us-east-1): "
set /p CLUSTER_NAME="Enter the EKS cluster name: "
set /p IMAGE_URI="Enter the image URI (registry path and tag): "

if "!AWS_REGION!"=="" (echo ERROR the AWS region is required & exit /b 1)
if "!CLUSTER_NAME!"=="" (echo ERROR the cluster name is required & exit /b 1)
if "!IMAGE_URI!"=="" (echo ERROR the image URI is required & exit /b 1)

set "DB_JDBC_URL="
set "DB_USER="
set "DB_PASSWORD="
set /p DB_JDBC_URL="Enter value for DB_JDBC_URL (or press Enter to skip): "
set /p DB_USER="Enter value for DB_USER (or press Enter to skip): "
powershell -NoProfile -Command "$p = Read-Host 'Enter value for DB_PASSWORD (hidden, or press Enter to skip)' -AsSecureString; [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($p))" > "%TEMP%\cz_db_password.tmp"
set /p DB_PASSWORD=<"%TEMP%\cz_db_password.tmp"
del "%TEMP%\cz_db_password.tmp" >nul 2>&1

echo Configuring kubectl for the cluster !CLUSTER_NAME!
aws eks update-kubeconfig --region !AWS_REGION! --name !CLUSTER_NAME!
if !ERRORLEVEL! neq 0 (echo ERROR could not configure kubectl for the cluster. Check the region, the cluster name and your IAM permissions & exit /b 1)
kubectl cluster-info
if !ERRORLEVEL! neq 0 (echo ERROR kubectl cannot reach the cluster & exit /b 1)

rem The manifests are filled in place; the originals are restored when the script ends
set "BACKUP_DIR=%TEMP%\cz_k8s_backup"
if exist "!BACKUP_DIR!" rmdir /s /q "!BACKUP_DIR!"
mkdir "!BACKUP_DIR!"
copy /y kubernetes\*.yaml "!BACKUP_DIR!" >nul

for %%F in (namespace deployment service ingress) do (
  powershell -NoProfile -Command "$f = 'kubernetes\%%F.yaml'; (Get-Content -Raw $f).Replace('{{IMAGE_URI}}', $env:IMAGE_URI).Replace('{{NAMESPACE}}', $env:NAMESPACE).Replace('{{DB_JDBC_URL}}', $env:DB_JDBC_URL) | Set-Content -NoNewline $f"
  if !ERRORLEVEL! neq 0 (echo ERROR could not fill in the manifest %%F & goto fail)
)

echo Applying the namespace
kubectl apply -f kubernetes\namespace.yaml
if !ERRORLEVEL! neq 0 (echo ERROR could not apply the namespace & goto fail)

echo Creating the Secret !APP_NAME!-secrets
kubectl create secret generic !APP_NAME!-secrets -n !NAMESPACE! --from-literal=DB_USER="!DB_USER!" --from-literal=DB_PASSWORD="!DB_PASSWORD!" --dry-run=client -o yaml | kubectl apply -f -
if !ERRORLEVEL! neq 0 (echo ERROR could not create the Secret & goto fail)

echo Applying the deployment, the service and the ingress
kubectl apply -f kubernetes\deployment.yaml
if !ERRORLEVEL! neq 0 (echo ERROR could not apply deployment.yaml & goto fail)
kubectl apply -f kubernetes\service.yaml
if !ERRORLEVEL! neq 0 (echo ERROR could not apply service.yaml & goto fail)
kubectl apply -f kubernetes\ingress.yaml
if !ERRORLEVEL! neq 0 (echo ERROR could not apply ingress.yaml & goto fail)

echo Waiting for the rollout
kubectl rollout status deployment/!APP_NAME! -n !NAMESPACE! --timeout=600s
if !ERRORLEVEL! neq 0 (
  echo ERROR the rollout failed
  echo Rollback with kubectl rollout undo deployment/!APP_NAME! -n !NAMESPACE!
  echo Inspect with kubectl describe pods -n !NAMESPACE! and kubectl logs deployment/!APP_NAME! -n !NAMESPACE!
  goto fail
)

kubectl get pods,svc,ingress -n !NAMESPACE!

set "INGRESS_HOST="
kubectl get ingress !APP_NAME!-ingress -n !NAMESPACE! -o jsonpath="{.status.loadBalancer.ingress[0].hostname}" > "%TEMP%\cz_ingress_host.tmp" 2>nul
set /p INGRESS_HOST=<"%TEMP%\cz_ingress_host.tmp"
del "%TEMP%\cz_ingress_host.tmp" >nul 2>&1

if "!INGRESS_HOST!"=="" (
  echo The load balancer address is not ready yet. Check later with kubectl get ingress -n !NAMESPACE!
) else (
  echo The application is served over HTTP at the address !INGRESS_HOST! and its health check is at /rest/health
)
echo To roll back use kubectl rollout undo deployment/!APP_NAME! -n !NAMESPACE!

call :restore
endlocal
exit /b 0

:fail
call :restore
endlocal
exit /b 1

:restore
copy /y "!BACKUP_DIR!\*.yaml" kubernetes\ >nul
rmdir /s /q "!BACKUP_DIR!" >nul 2>&1
exit /b 0
