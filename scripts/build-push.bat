@echo off
setlocal enabledelayedexpansion

echo -------------------------------------------------------
echo Cargo Tracker - Build and Push Script (Windows)
echo -------------------------------------------------------

set PROJECT_NAME=cargo-tracker

set /p IMAGE_TAG="Enter image tag [latest]: "
if "!IMAGE_TAG!"=="" set IMAGE_TAG=latest

:: Sanitize Image Tag (simple trim)
for /f "tokens=*" %%a in ("!IMAGE_TAG!") do set IMAGE_TAG=%%a

echo Select Registry:
echo 1) AWS ECR
echo 2) Docker Hub
set /p REGISTRY_CHOICE="Choice [1-2]: "

if "!REGISTRY_CHOICE!"=="1" (
    set /p AWS_REGION="Enter AWS Region [us-east-1]: "
    if "!AWS_REGION!"=="" set AWS_REGION=us-east-1
    set /p ECR_REPO="Enter ECR Repository Name [cargo-tracker]: "
    if "!ECR_REPO!"=="" set ECR_REPO=cargo-tracker
    
    echo Logging into AWS ECR...
    aws ecr get-login-password --region !AWS_REGION! | docker login --username AWS --password-stdin !REGISTRY_URL!
    if !ERRORLEVEL! neq 0 (
        echo ECR login failed
        exit /b 1
    )
    
    echo Checking if ECR repository !ECR_REPO! exists...
    aws ecr describe-repositories --repository-names !ECR_REPO! --region !AWS_REGION! >nul 2>&1
    if !ERRORLEVEL! neq 0 (
        echo Creating ECR repository !ECR_REPO!...
        aws ecr create-repository --repository-name !ECR_REPO! --region !AWS_REGION!
    )
    
    set REGISTRY_URL=aws_account_id.dkr.ecr. !AWS_REGION!.amazonaws.com
    set FULL_IMAGE_NAME=!REGISTRY_URL!/!ECR_REPO!:!IMAGE_TAG!
) else (
    set /p DOCKER_USERNAME="Enter Docker Hub Username: "
    set /p DOCKER_PASSWORD="Enter Docker Hub Password: "
    
    echo Logging into Docker Hub...
    echo !DOCKER_PASSWORD! | docker login --username !DOCKER_USERNAME! --password-stdin
    if !ERRORLEVEL! neq 0 (
        echo Docker Hub login failed
        exit /b 1
    )
    
    set FULL_IMAGE_NAME=!DOCKER_USERNAME!/cargo-tracker:!IMAGE_TAG!
)

echo Building Docker image: !FULL_IMAGE_NAME!...
docker build -t !FULL_IMAGE_NAME! .
if !ERRORLEVEL! neq 0 (
    echo Docker build failed
    exit /b 1
)

echo Pushing Docker image to registry...
docker push !FULL_IMAGE_NAME!
if !ERRORLEVEL! neq 0 (
    echo Docker push failed
    exit /b 1
)

echo -------------------------------------------------------
echo Successfully built and pushed: !FULL_IMAGE_NAME!
echo -------------------------------------------------------
