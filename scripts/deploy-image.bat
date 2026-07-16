@echo off
setlocal enabledelayedexpansion

set PROJECT_NAME=cargotracker

echo --- AWS EKS Deployment Script ---

set /p AWS_REGION="Enter AWS Region [us-east-1]: "
if "!AWS_REGION!"=="" set AWS_REGION=us-east-1

set /p CLUSTER_NAME="Enter EKS Cluster Name: "
if "!CLUSTER_NAME!"=="" (echo Cluster name is required & exit /b 1)

set /p IMAGE_URI="Enter Docker Image URI: "
if "!IMAGE_URI!"=="" (echo Image URI is required & exit /b 1)

echo Enter application configuration (press Enter to skip):
set /p DB_JDBC_URL="DB_JDBC_URL: "
set /p DB_USER="DB_USER: "
set /p DB_PASSWORD="DB_PASSWORD: "

echo Updating manifests...
powershell -Command "(Get-Content kubernetes/deployment.yaml) -replace '{{IMAGE_URI}}', '!IMAGE_URI!' -replace '{{DB_JDBC_URL}}', '!DB_JDBC_URL!' -replace '{{DB_USER}}', '!DB_USER!' -replace '{{DB_PASSWORD}}', '!DB_PASSWORD!' | Set-Content kubernetes/deployment.yaml"

echo Configuring kubectl for EKS...
aws eks update-kubeconfig --region !AWS_REGION! --name !CLUSTER_NAME!

echo Verifying cluster connectivity...
kubectl cluster-info
if !ERRORLEVEL! neq 0 (echo Cluster connectivity failed & exit /b 1)

echo Applying Kubernetes manifests...
kubectl apply -f kubernetes/namespace.yaml
kubectl apply -f kubernetes/deployment.yaml
kubectl apply -f kubernetes/service.yaml
kubectl apply -f kubernetes/ingress.yaml

echo Waiting for rollout...
kubectl rollout status deployment/!PROJECT_NAME! -n !PROJECT_NAME!

echo Verifying resources...
kubectl get pods,svc,ingress -n !PROJECT_NAME!

echo Deployment complete!
echo Application URL: http://cargotracker.example.com
echo If deployment failed, use: kubectl rollout undo deployment/!PROJECT_NAME! -n !PROJECT_NAME!
