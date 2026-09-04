@echo off
setlocal enabledelayedexpansion

echo ============================================
echo   Cargo Tracker - Docker Build ^& Push
echo ============================================
echo.

set "PROJECT_NAME=cargo-tracker"

REM Sanitize image name to lowercase alphanumeric with hyphens
for /f "delims=" %%i in ('powershell -Command "\"cargo-tracker\" -replace \"[^a-z0-9]\",\"-\" -replace \"^-+\",\"\" -replace \"-+$\",\"\""') do set "IMAGE_NAME=%%i"
if "!IMAGE_NAME!"=="" set "IMAGE_NAME=cargo-tracker"

REM Prompt for image tag
set /p "IMAGE_TAG_INPUT=Enter image tag (press Enter for 'latest'): "
if "!IMAGE_TAG_INPUT!"=="" (
    set "IMAGE_TAG=latest"
) else (
    for /f "delims=" %%t in ('powershell -Command "\"!IMAGE_TAG_INPUT!\" -replace \"[^a-z0-9._-]\",\"-\" -replace \"^-+\",\"\" -replace \"-+$\",\"\""') do set "IMAGE_TAG=%%t"
    if "!IMAGE_TAG!"=="" set "IMAGE_TAG=latest"
)
echo Using image tag: !IMAGE_TAG!
echo.

REM Prompt for registry type
echo Select container registry:
echo   1. AWS ECR
echo   2. Docker Hub
set /p "REGISTRY_CHOICE=Enter choice (1 or 2): "

if "!REGISTRY_CHOICE!"=="1" (
    REM AWS ECR
    set /p "AWS_REGION=Enter AWS Region (e.g. us-east-1): "
    set /p "AWS_ACCOUNT_ID=Enter AWS Account ID: "
    set "ECR_REPO=!IMAGE_NAME!"
    set "REGISTRY_URL=!AWS_ACCOUNT_ID!.dkr.ecr.!AWS_REGION!.amazonaws.com"
    set "FULL_IMAGE_NAME=!REGISTRY_URL!/!ECR_REPO!:!IMAGE_TAG!"

    echo.
    echo Logging in to AWS ECR...
    aws ecr get-login-password --region !AWS_REGION! | docker login --username AWS --password-stdin !REGISTRY_URL!
    if !ERRORLEVEL! neq 0 (
        echo ERROR: ECR login failed.
        exit /b 1
    )

    REM Auto-create ECR repository if it does not exist
    aws ecr describe-repositories --repository-names !ECR_REPO! --region !AWS_REGION! >nul 2>&1
    if !ERRORLEVEL! neq 0 (
        echo Creating ECR repository !ECR_REPO!...
        aws ecr create-repository --repository-name !ECR_REPO! --region !AWS_REGION!
    )

) else if "!REGISTRY_CHOICE!"=="2" (
    REM Docker Hub
    set /p "DOCKER_USERNAME=Enter Docker Hub username: "
    set /p "DOCKER_PASSWORD=Enter Docker Hub password/token: "
    set "REGISTRY_URL=docker.io"
    set "FULL_IMAGE_NAME=!DOCKER_USERNAME!/!IMAGE_NAME!:!IMAGE_TAG!"

    echo.
    echo Logging in to Docker Hub...
    echo !DOCKER_PASSWORD! | docker login --username !DOCKER_USERNAME! --password-stdin
    if !ERRORLEVEL! neq 0 (
        echo ERROR: Docker Hub login failed.
        exit /b 1
    )

) else (
    echo ERROR: Invalid choice. Please enter 1 or 2.
    exit /b 1
)

echo.
echo Building Docker image: !FULL_IMAGE_NAME!
docker build -f Dockerfile -t "!FULL_IMAGE_NAME!" .
if !ERRORLEVEL! neq 0 (
    echo ERROR: Docker build failed.
    exit /b 1
)

echo.
echo Pushing image: !FULL_IMAGE_NAME!
docker push "!FULL_IMAGE_NAME!"
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
