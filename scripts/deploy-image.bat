@echo off
setlocal enabledelayedexpansion

echo -------------------------------------------------------
echo Cargo Tracker - Deploy to AWS EKS (Windows)
echo -------------------------------------------------------

set /p AWS_REGION="Enter AWS Region [us-east-1]: "
if "!AWS_REGION!"=="" set AWS_REGION=us-east-1

set /p CLUSTER_NAME="Enter EKS Cluster Name: "
if "!CLUSTER_NAME!"=="" (
    echo Error: Cluster name is required.
    exit /b 1
)

set /p IMAGE_URI="Enter Docker Image URI: "
if "!IMAGE_URI!"=="" (
    echo Error: Image URI is required.
    exit /b 1
)

echo Enter application configuration (press Enter to skip):
set /p DB_JDBC_URL="DB_JDBC_URL: "
set /p DB_USER="DB_USER: "
set /p DB_PASSWORD="DB_PASSWORD: "

echo Updating manifests...
:: Using PowerShell for sed-like replacement on Windows
powershell -Command "(gc kubernetes/deployment.yaml) -replace '{{IMAGE_URI}}', '!IMAGE_URI!' | Out-File -encoding utf8 kubernetes/deployment.yaml"
powershell -Command "(gc kubernetes/deployment.yaml) -replace '{{DB_JDBC_URL}}', '!DB_JDBC_URL!' | Out-File -encoding utf8 kubernetes/deployment.yaml"
powershell -Command "(gc kubernetes/deployment.yaml) -replace '{{DB_USER}}', '!DB_USER!' | Out-File -encoding utf8 kubernetes/deployment.yaml"
powershell -Command "(gc kubernetes/deployment.yaml) -replace '{{DB_PASSWORD}}', '!DB_PASSWORD!' | Out-File -encoding utf8 kubernetes/deployment.yaml"

echo Configuring kubectl...
aws eks update-kubeconfig --region !AWS_REGION! --name !CLUSTER_NAME!

echo Verifying cluster connectivity...
kubectl cluster-info
if !ERRORLEVEL! neq 0 (
    echo Error: Could not connect to EKS cluster
    exit /b 1
)

echo Applying Kubernetes manifests...
kubectl apply -f kubernetes/namespace.yaml
kubectl apply -f kubernetes/deployment.yaml
kubectl apply -f kubernetes/service.yaml
kubectl apply -f kubernetes/ingress.yaml

echo Waiting for deployment rollout...
kubectl rollout status deployment/cargo-tracker -n cargo-tracker

echo Verifying deployed resources...
kubectl get pods,svc,ingress -n cargo-tracker

echo -------------------------------------------------------
echo Deployment Complete!
echo Application should be available at cargo-tracker.example.com
echo -------------------------------------------------------
echo Rollback instructions: kubectl rollout undo deployment/cargo-tracker -n cargo-tracker
