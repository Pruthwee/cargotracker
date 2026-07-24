@echo off
setlocal enabledelayedexpansion

set PROJECT_NAME=cargotracker

echo -------------------------------------------------------
echo Build and Push Docker Image for %PROJECT_NAME%
echo -------------------------------------------------------

set /p IMAGE_TAG="Enter image tag [latest]: "
if "!IMAGE_TAG!"=="" set IMAGE_TAG=latest

:: Sanitize Image Tag using PowerShell
for /f "usebackq tokens=*" %%i in (`powershell -Command "[System.Text.RegularExpressions.Regex]::Replace('!IMAGE_TAG!'.ToLower(), '[^a-z0-9.]', '-').Trim('-')"`) do set IMAGE_TAG=%%i

echo Select Registry:
echo 1) AWS ECR
echo 2) Docker Hub
set /p REGISTRY_CHOICE="Choice [1-2]: "

if "!REGISTRY_CHOICE!"=="1" (
    set /p AWS_REGION="Enter AWS Region [us-east-1]: "
    if "!AWS_REGION!"=="" set AWS_REGION=us-east-1
    set /p ECR_REPO="Enter ECR Repository Name: "
    
    :: Sanitize ECR Repo Name
    for /f "usebackq tokens=*" %%i in (`powershell -Command "[System.Text.RegularExpressions.Regex]::Replace('!ECR_REPO!'.ToLower(), '[^a-z0-9]', '-').Trim('-')"`) do set ECR_REPO=%%i
    
    for /f "usebackq tokens=*" %%i in (`aws sts get-caller-identity --query Account --output text`) do set ACCOUNT_ID=%%i
    set REGISTRY_URL=!ACCOUNT_ID!.dkr.ecr.!AWS_REGION!.amazonaws.com
    set FULL_IMAGE_NAME=!REGISTRY_URL!/!ECR_REPO!:!IMAGE_TAG!
    
    echo Logging into AWS ECR...
    aws ecr get-login-password --region !AWS_REGION! | docker login --username AWS --password-stdin !REGISTRY_URL!
    if !ERRORLEVEL! neq 0 (
        echo ECR login failed
        exit /b 1
    )
    
    echo Checking if ECR repository !ECR_REPO! exists...
    aws ecr describe-repositories --repository-names !ECR_REPO! --region !AWS_REGION! >nul 2>&1
    if !ERRORLEVEL! neq 0 (
        echo Creating ECR repository...
        aws ecr create-repository --repository-name !ECR_REPO! --region !AWS_REGION!
    )
) else if "!REGISTRY_CHOICE!"=="2" (
    set /p DOCKER_USERNAME="Enter Docker Hub Username: "
    set /p DOCKER_REPO="Enter Docker Hub Repository Name: "
    
    for /f "usebackq tokens=*" %%i in (`powershell -Command "[System.Text.RegularExpressions.Regex]::Replace('!DOCKER_REPO!'.ToLower(), '[^a-z0-9]', '-').Trim('-')"`) do set DOCKER_REPO=%%i
    set FULL_IMAGE_NAME=!DOCKER_USERNAME!/!DOCKER_REPO!:!IMAGE_TAG!
    
    echo Logging into Docker Hub...
    docker login -u !DOCKER_USERNAME!
) else (
    echo Invalid choice. Exiting.
    exit /b 1
)

echo Building Docker image: !FULL_IMAGE_NAME!
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
