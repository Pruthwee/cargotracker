@echo off
setlocal enabledelayedexpansion

set PROJECT_NAME=cargo-tracker

echo -------------------------------------------------------
echo Build and Push Docker Image for %PROJECT_NAME%
echo -------------------------------------------------------

set /p IMAGE_TAG="Enter image tag [latest]: "
if "!IMAGE_TAG!"=="" set IMAGE_TAG=latest

:: Sanitize Image Name using PowerShell
for /f "usebackq tokens=*" %%i in (`powershell -command "'%PROJECT_NAME%'.ToLower().Replace(' ', '-').Replace('[^a-z0-9-]', '')"`) do set IMAGE_NAME=%%i
set IMAGE_NAME=!IMAGE_NAME!

echo Registry Selection:
echo 1. AWS ECR
echo 2. Docker Hub
set /p REGISTRY_CHOICE="Select registry (1 or 2): "

if "!REGISTRY_CHOICE!"=="1" (
    set /p AWS_REGION="Enter AWS Region [us-east-1]: "
    if "!AWS_REGION!"=="" set AWS_REGION=us-east-1
    set /p ECR_REPO="Enter ECR Repository Name [!IMAGE_NAME!]: "
    if "!ECR_REPO!"=="" set ECR_REPO=!IMAGE_NAME!
    
    for /f "usebackq tokens=*" %%i in (`aws sts get-caller-identity --query Account --output text`) do set ACCOUNT_ID=%%i
    set REGISTRY_URL=!ACCOUNT_ID!.dkr.ecr.!AWS_REGION!.amazonaws.com
    set FULL_IMAGE_NAME=!REGISTRY_URL!/!ECR_REPO!:!IMAGE_TAG!
    
    echo Logging into AWS ECR...
    aws ecr get-login-password --region !AWS_REGION! | docker login --username AWS --password-stdin !REGISTRY_URL!
    if !ERRORLEVEL! neq 0 (
        echo ECR login failed
        exit /b 1
    )
    
    echo Ensuring ECR repository exists...
    aws ecr describe-repositories --repository-names !ECR_REPO! --region !AWS_REGION! >nul 2>&1
    if !ERRORLEVEL! neq 0 (
        echo Creating ECR repository...
        aws ecr create-repository --repository-name !ECR_REPO! --region !AWS_REGION!
    )
) else if "!REGISTRY_CHOICE!"=="2" (
    set /p DOCKER_USERNAME="Enter Docker Hub Username: "
    set REGISTRY_URL=docker.io/!DOCKER_USERNAME!
    set FULL_IMAGE_NAME=!REGISTRY_URL!/!IMAGE_NAME!:!IMAGE_TAG!
    
    echo Logging into Docker Hub...
    docker login -u !DOCKER_USERNAME!
) else (
    echo Invalid choice. Exiting.
    exit /b 1
)

echo Building Docker image: !FULL_IMAGE_NAME!...
docker build -t "!FULL_IMAGE_NAME!" .
if !ERRORLEVEL! neq 0 (
    echo Docker build failed
    exit /b 1
)

echo Pushing Docker image to registry...
docker push "!FULL_IMAGE_NAME!"
if !ERRORLEVEL! neq 0 (
    echo Docker push failed
    exit /b 1
)

echo -------------------------------------------------------
echo Successfully built and pushed: !FULL_IMAGE_NAME!
echo -------------------------------------------------------
