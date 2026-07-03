@echo off
setlocal enabledelayedexpansion

set PROJECT_NAME=cargo-tracker

echo --- AWS EKS Deployment ---
set /p AWS_REGION="Enter AWS Region (e.g., us-east-1): "
set /p CLUSTER_NAME="Enter EKS Cluster Name: "
set /p IMAGE_URI="Enter Docker Image URI (full path with tag): "

set /p DB_JDBC_URL="Enter DB_JDBC_URL (or press Enter to skip): "
set /p DB_USER="Enter DB_USER (or press Enter to skip): "
set /p DB_PASSWORD="Enter DB_PASSWORD (or press Enter to skip): "

echo Updating manifests...
powershell -Command "(gc kubernetes/deployment.yaml) -replace '{{IMAGE_URI}}', '%IMAGE_URI%' -replace '{{DB_JDBC_URL}}', '%DB_JDBC_URL%' -replace '{{DB_USER}}', '%DB_USER%' -replace '{{DB_PASSWORD}}', '%DB_PASSWORD%' | Out-File -encoding utf8 kubernetes/deployment.yaml"

echo Configuring kubectl...
aws eks update-kubeconfig --region %AWS_REGION% --name %CLUSTER_NAME%

echo Verifying cluster connectivity...
kubectl cluster-info
if %ERRORLEVEL% neq 0 (echo Cluster connectivity failed & exit /b 1)

echo Applying manifests...
kubectl apply -f kubernetes/namespace.yaml
kubectl apply -f kubernetes/deployment.yaml
kubectl apply -f kubernetes/service.yaml
kubectl apply -f kubernetes/ingress.yaml

echo Waiting for rollout...
kubectl rollout status deployment/%PROJECT_NAME% -n %PROJECT_NAME%

echo Verifying resources...
kubectl get pods,svc,ingress -n %PROJECT_NAME%

echo Deployment complete. Application URL: http://cargo-tracker.example.com
echo To rollback: kubectl rollout undo deployment/%PROJECT_NAME% -n %PROJECT_NAME%
