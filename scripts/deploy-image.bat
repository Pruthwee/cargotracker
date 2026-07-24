@echo off
setlocal enabledelayedexpansion

set PROJECT_NAME=cargotracker

echo -------------------------------------------------------
echo Deploying %PROJECT_NAME% to AWS EKS
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
set /p POSTGRESQL_JDBC_URL="POSTGRESQL_JDBC_URL [jdbc:postgresql://postgres-db:5432/cargotracker]: "
if "!POSTGRESQL_JDBC_URL!"=="" set POSTGRESQL_JDBC_URL=jdbc:postgresql://postgres-db:5432/cargotracker

set /p POSTGRESQL_USERNAME="POSTGRESQL_USERNAME [postgres]: "
if "!POSTGRESQL_USERNAME!"=="" set POSTGRESQL_USERNAME=postgres

set /p POSTGRESQL_PASSWORD="POSTGRESQL_PASSWORD: "

echo Updating Kubernetes manifests...
powershell -Command "(Get-Content kubernetes/deployment.yaml) -replace '{{IMAGE_URI}}', '!IMAGE_URI!' -replace '{{POSTGRESQL_JDBC_URL}}', '!POSTGRESQL_JDBC_URL!' -replace '{{POSTGRESQL_USERNAME}}', '!POSTGRESQL_USERNAME!' -replace '{{POSTGRESQL_PASSWORD}}', '!POSTGRESQL_PASSWORD!' | Set-Content kubernetes/deployment.yaml"

echo Configuring kubectl for EKS...
aws eks update-kubeconfig --region !AWS_REGION! --name !CLUSTER_NAME!

echo Verifying cluster connectivity...
kubectl cluster-info
if !ERRORLEVEL! neq 0 (
    echo Error: Cannot connect to EKS cluster
    exit /b 1
)

echo Applying Kubernetes manifests...
kubectl apply -f kubernetes/namespace.yaml
kubectl apply -f kubernetes/deployment.yaml
kubectl apply -f kubernetes/service.yaml
kubectl apply -f kubernetes/ingress.yaml

echo Waiting for deployment rollout...
kubectl rollout status deployment/%PROJECT_NAME% -n %PROJECT_NAME%

echo Verifying resources...
kubectl get pods,svc,ingress -n %PROJECT_NAME%

echo -------------------------------------------------------
echo Deployment successful!
echo Application is available via the Ingress URL.
echo -------------------------------------------------------
echo To rollback in case of failure: kubectl rollout undo deployment/%PROJECT_NAME% -n %PROJECT_NAME%
