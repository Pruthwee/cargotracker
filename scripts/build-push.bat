@echo off
setlocal enabledelayedexpansion

rem ============================================================
rem build-push.bat  -  Build and push cargo-tracker Docker image
rem ============================================================

set PROJECT_NAME=cargo-tracker
set DOCKERFILE=Dockerfile

echo ==============================================
echo   cargo-tracker  -  Build ^& Push
echo ==============================================

rem Sanitize image name (PowerShell-based lowercase + hyphenation)
for /f "delims=" %%i in ('powershell -NoProfile -Command "\"cargo-tracker\" -replace '[^a-z0-9]','-' -replace '^-+','' -replace '-+$',''"') do set IMAGE_NAME=%%i

rem Prompt for image tag
set /p IMAGE_TAG_INPUT="Enter image tag [latest]: "
if "!IMAGE_TAG_INPUT!"=="" set IMAGE_TAG_INPUT=latest
for /f "delims=" %%i in ('powershell -NoProfile -Command "\"!IMAGE_TAG_INPUT!\" -replace '[^a-z0-9._-]','-' -replace '^-+','' -replace '-+$','' -replace '  +',' '"') do set IMAGE_TAG=%%i
if "!IMAGE_TAG!"=="" set IMAGE_TAG=latest

echo.
echo Select container registry:
echo   1) AWS ECR
echo   2) Docker Hub
set /p REGISTRY_CHOICE="Enter choice [1]: "
if "!REGISTRY_CHOICE!"=="" set REGISTRY_CHOICE=1

rem -------------------------------------------------------
rem AWS ECR
rem -------------------------------------------------------
if "!REGISTRY_CHOICE!"=="1" (
    set /p AWS_REGION="Enter AWS region [us-east-1]: "
    if "!AWS_REGION!"=="" set AWS_REGION=us-east-1

    set /p AWS_ACCOUNT_ID="Enter AWS account ID: "
    if "!AWS_ACCOUNT_ID!"=="" (
        echo ERROR: AWS account ID is required.
        exit /b 1
    )

    set /p ECR_REPO_INPUT="Enter ECR repository name [!IMAGE_NAME!]: "
    if "!ECR_REPO_INPUT!"=="" (
        set ECR_REPO=!IMAGE_NAME!
    ) else (
        set ECR_REPO=!ECR_REPO_INPUT!
    )

    set REGISTRY_URL=!AWS_ACCOUNT_ID!.dkr.ecr.!AWS_REGION!.amazonaws.com
    set FULL_IMAGE_NAME=!REGISTRY_URL!/!ECR_REPO!:!IMAGE_TAG!

    echo.
    echo Authenticating with ECR...
    aws ecr get-login-password --region !AWS_REGION! | docker login --username AWS --password-stdin !REGISTRY_URL!
    if !ERRORLEVEL! neq 0 (
        echo ERROR: ECR login failed.
        exit /b 1
    )

    echo Ensuring ECR repository exists...
    aws ecr describe-repositories --repository-names !ECR_REPO! --region !AWS_REGION! >nul 2>&1
    if !ERRORLEVEL! neq 0 (
        echo Creating ECR repository...
        aws ecr create-repository --repository-name !ECR_REPO! --region !AWS_REGION!
        if !ERRORLEVEL! neq 0 (
            echo ERROR: Failed to create ECR repository.
            exit /b 1
        )
    )
    goto BUILD
)

rem -------------------------------------------------------
rem Docker Hub
rem -------------------------------------------------------
if "!REGISTRY_CHOICE!"=="2" (
    set /p DOCKER_USERNAME="Enter Docker Hub username: "
    if "!DOCKER_USERNAME!"=="" (
        echo ERROR: Docker Hub username is required.
        exit /b 1
    )

    set /p DOCKER_PASSWORD="Enter Docker Hub password/token: "
    if "!DOCKER_PASSWORD!"=="" (
        echo ERROR: Docker Hub password is required.
        exit /b 1
    )

    set /p REPO_INPUT="Enter Docker Hub repository name [!IMAGE_NAME!]: "
    if "!REPO_INPUT!"=="" (
        set REPO=!IMAGE_NAME!
    ) else (
        set REPO=!REPO_INPUT!
    )

    set FULL_IMAGE_NAME=!DOCKER_USERNAME!/!REPO!:!IMAGE_TAG!

    echo.
    echo Authenticating with Docker Hub...
    echo !DOCKER_PASSWORD! | docker login --username !DOCKER_USERNAME! --password-stdin
    if !ERRORLEVEL! neq 0 (
        echo ERROR: Docker Hub login failed.
        exit /b 1
    )
    goto BUILD
)

echo ERROR: Invalid registry choice.
exit /b 1

:BUILD
echo.
echo Building Docker image: !FULL_IMAGE_NAME!
docker build -f !DOCKERFILE! -t !FULL_IMAGE_NAME! .
if !ERRORLEVEL! neq 0 (
    echo ERROR: Docker build failed.
    exit /b 1
)

echo.
echo Pushing image: !FULL_IMAGE_NAME!
docker push !FULL_IMAGE_NAME!
if !ERRORLEVEL! neq 0 (
    echo ERROR: Docker push failed.
    exit /b 1
)

echo.
echo ==============================================
echo   Build ^& Push complete!
echo   Image: !FULL_IMAGE_NAME!
echo ==============================================

endlocal
