@echo off
setlocal enabledelayedexpansion

set PROJECT_NAME=cargo-tracker

echo Select Registry Type:
echo 1. AWS ECR
echo 2. Docker Hub
set /p REGISTRY_CHOICE="Choice [1-2]: "

if "%REGISTRY_CHOICE%"=="1" (
    set /p AWS_REGION="Enter AWS Region (e.g., us-east-1): "
    set /p ECR_REPO="Enter ECR Repository Name: "
    
    for /f "tokens=*" %%i in ('aws ecr get-login-password --region !AWS_REGION!') do set PASSWORD=%%i
    echo !PASSWORD! | docker login --username AWS --password-stdin !AWS_ACCOUNT_ID!.dkr.ecr.!AWS_REGION!.amazonaws.com
    if !ERRORLEVEL! neq 0 (echo ECR login failed & exit /b 1)
    
    aws ecr describe-repositories --repository-names !ECR_REPO! --region !AWS_REGION! >nul 2>&1
    if !ERRORLEVEL! neq 0 (
        echo Creating ECR repository...
        aws ecr create-repository --repository-name !ECR_REPO! --region !AWS_REGION!
    )
    
    set REGISTRY_URL=!AWS_ACCOUNT_ID!.dkr.ecr.!AWS_REGION!.amazonaws.com
    set REPO_NAME=!ECR_REPO!
) else (
    set /p DOCKER_USERNAME="Enter Docker Hub Username: "
    set /p DOCKER_PASSWORD="Enter Docker Hub Password: "
    echo !DOCKER_PASSWORD! | docker login --username !DOCKER_USERNAME! --password-stdin
    if !ERRORLEVEL! neq 0 (echo Docker Hub login failed & exit /b 1)
    
    set REGISTRY_URL=!DOCKER_USERNAME!
    set REPO_NAME=%PROJECT_NAME%
)

set /p IMAGE_TAG="Enter Image Tag (default: latest): "
if "!IMAGE_TAG!"=="" set IMAGE_TAG=latest

set IMAGE_NAME=%PROJECT_NAME%
set FULL_IMAGE_NAME=!REGISTRY_URL!/!IMAGE_NAME!:!IMAGE_TAG!

echo Building image: !FULL_IMAGE_NAME!...
docker build -t !FULL_IMAGE_NAME! .
if !ERRORLEVEL! neq 0 (echo Docker build failed & exit /b 1)

echo Pushing image...
docker push !FULL_IMAGE_NAME!
if !ERRORLEVEL! neq 0 (echo Docker push failed & exit /b 1)

echo Successfully built and pushed !FULL_IMAGE_NAME!
