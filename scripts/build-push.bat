@echo off
setlocal enabledelayedexpansion

REM ============================================================
REM build-push.bat - Build and push Docker image for cargo-tracker
REM ============================================================

set PROJECT_NAME=cargo-tracker
set IMAGE_NAME=cargo-tracker

echo ============================================
echo   cargo-tracker - Docker Build ^& Push
echo ============================================
echo.

REM Prompt for image tag
set /p IMAGE_TAG_INPUT="Enter image tag (press Enter for 'latest'): "
if "!IMAGE_TAG_INPUT!"=="" (
    set IMAGE_TAG=latest
) else (
    set IMAGE_TAG=!IMAGE_TAG_INPUT!
)
echo Using tag: !IMAGE_TAG!
echo.

REM Prompt for registry type
echo Select container registry:
echo   1. AWS ECR
echo   2. Docker Hub
set /p REGISTRY_CHOICE="Enter choice (1 or 2): "
echo.

if "!REGISTRY_CHOICE!"=="1" (
    REM ---- AWS ECR ----
    set /p AWS_REGION="Enter AWS Region (e.g., us-east-1): "
    set /p AWS_ACCOUNT_ID="Enter AWS Account ID: "
    set /p ECR_REPO_INPUT="Enter ECR Repository name (default: !IMAGE_NAME!): "
    if "!ECR_REPO_INPUT!"=="" (
        set ECR_REPO=!IMAGE_NAME!
    ) else (
        set ECR_REPO=!ECR_REPO_INPUT!
    )
    set REGISTRY_URL=!AWS_ACCOUNT_ID!.dkr.ecr.!AWS_REGION!.amazonaws.com
    set FULL_IMAGE_NAME=!REGISTRY_URL!/!ECR_REPO!:!IMAGE_TAG!

    echo Logging in to AWS ECR...
    aws ecr get-login-password --region !AWS_REGION! | docker login --username AWS --password-stdin !REGISTRY_URL!
    if !ERRORLEVEL! neq 0 (
        echo ERROR: ECR login failed.
        exit /b 1
    )

    echo Checking/creating ECR repository...
    aws ecr describe-repositories --repository-names !ECR_REPO! --region !AWS_REGION! >nul 2>&1
    if !ERRORLEVEL! neq 0 (
        echo Creating ECR repository...
        aws ecr create-repository --repository-name !ECR_REPO! --region !AWS_REGION!
    )

) else if "!REGISTRY_CHOICE!"=="2" (
    REM ---- Docker Hub ----
    set /p DOCKER_USERNAME="Enter Docker Hub username: "
    set /p DOCKER_PASSWORD="Enter Docker Hub password/token: "
    set /p DOCKER_REPO_INPUT="Enter Docker Hub repository (default: !DOCKER_USERNAME!/!IMAGE_NAME!): "
    if "!DOCKER_REPO_INPUT!"=="" (
        set DOCKER_REPO=!DOCKER_USERNAME!/!IMAGE_NAME!
    ) else (
        set DOCKER_REPO=!DOCKER_REPO_INPUT!
    )
    set FULL_IMAGE_NAME=!DOCKER_REPO!:!IMAGE_TAG!

    echo Logging in to Docker Hub...
    echo !DOCKER_PASSWORD! | docker login --username !DOCKER_USERNAME! --password-stdin
    if !ERRORLEVEL! neq 0 (
        echo ERROR: Docker Hub login failed.
        exit /b 1
    )

) else (
    echo ERROR: Invalid registry choice. Please enter 1 or 2.
    exit /b 1
)

echo.
echo Building Docker image: !FULL_IMAGE_NAME!
echo --------------------------------------------
docker build -f Dockerfile -t !FULL_IMAGE_NAME! .
if !ERRORLEVEL! neq 0 (
    echo ERROR: Docker build failed.
    exit /b 1
)

echo.
echo Pushing Docker image: !FULL_IMAGE_NAME!
echo --------------------------------------------
docker push !FULL_IMAGE_NAME!
if !ERRORLEVEL! neq 0 (
    echo ERROR: Docker push failed.
    exit /b 1
)

echo.
echo ============================================
echo   SUCCESS: Image pushed successfully!
echo   Image: !FULL_IMAGE_NAME!
echo ============================================

endlocal
