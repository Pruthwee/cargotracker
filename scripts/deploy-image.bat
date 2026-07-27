@echo off
setlocal enabledelayedexpansion

set PROJECT_NAME=cargo-tracker
set NAMESPACE=cargo-tracker

echo -------------------------------------------------------
echo Deploying %PROJECT_NAME% to AWS EKS
echo -------------------------------------------------------

set /p AWS_REGION="Enter AWS Region [us-east-1]: "
if "!AWS_REGION!"=="" set AWS_REGION=us-east-1

set /p CLUSTER_NAME="Enter EKS Cluster Name: "
if "!CLUSTER_NAME!"=="" (
    echo Cluster name is required.
    exit /b 1
)

set /p IMAGE_URI="Enter full Docker Image URI: "
if "!IMAGE_URI!"=="" (
    echo Image URI is required.
    exit /b 1
)

echo Enter application configuration (press Enter to skip):
set /p DB_JDBC_URL="DB_JDBC_URL [jdbc:postgresql://postgres:5432/cargo]: "
if "!DB_JDBC_URL!"=="" set DB_JDBC_URL=jdbc:postgresql://postgres:5432/cargo
set /p DB_USER="DB_USER [postgres]: "
if "!DB_USER!"=="" set DB_USER=postgres
set /p DB_PASSWORD="DB_PASSWORD: "

echo Updating manifests...
:: Using PowerShell for sed-like replacement in Windows
powershell -Command "(Get-Content kubernetes/deployment.yaml) -replace '\{\{IMAGE_URI\}\}', '!IMAGE_URI!' -replace '\{\{DB_JDBC_URL\}\}', '!DB_JDBC_URL!' -replace '\{\{DB_USER\}\}', '!DB_USER!' -replace '\{\{DB_PASSWORD\}\}', '!DB_PASSWORD!' | Set-Content kubernetes/deployment.yaml"

echo Configuring kubectl for EKS...
aws eks update-kubeconfig --region !AWS_REGION! --name !CLUSTER_NAME!

echo Verifying cluster connectivity...
kubectl cluster-info
if !ERRORLEVEL! neq 0 (
    echo Failed to connect to EKS cluster
    exit /b 1
)

echo Applying manifests...
kubectl apply -f kubernetes/namespace.yaml
kubectl apply -f kubernetes/deployment.yaml
kubectl apply -f kubernetes/service.yaml
kubectl apply -f kubernetes/ingress.yaml

echo Waiting for deployment rollout...
kubectl rollout status deployment/%PROJECT_NAME% -n %NAMESPACE%

echo Verifying resources...
kubectl get pods,svc,ingress -n %NAMESPACE%

echo -------------------------------------------------------
echo Deployment complete!
echo Application should be accessible via the Ingress URL.
echo To rollback in case of failure: kubectl rollout undo deployment/%PROJECT_NAME% -n %NAMESPACE%
echo -------------------------------------------------------
