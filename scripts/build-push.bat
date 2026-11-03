@echo off
setlocal enabledelayedexpansion

REM Build and push script for cargo-tracker to AWS ECR or Docker Hub
REM Usage: scripts\build-push.bat

set "PROJECT_NAME=cargo-tracker"

echo === Cargo Tracker Build and Push ===

echo.
echo Select target platform:
echo   1. AWS ECR
echo   2. Docker Hub
set /p PLATFORM_CHOICE="Enter choice (1 or 2): "

if "!PLATFORM_CHOICE!"=="1" (
    set /p AWS_REGION="Enter AWS region (e.g., us-east-1): "
    set /p REGISTRY_URL="Enter ECR registry URL (e.g., 123456789.dkr.ecr.us-east-1.amazonaws.com): "
    set /p ECR_REPO="Enter ECR repository name: "
    set /p IMAGE_TAG="Enter image tag (or press Enter for 'latest'): "

    if "!IMAGE_TAG!"=="" set "IMAGE_TAG=latest"

    REM Sanitize image name using PowerShell
    for /f %%i in ('powershell -command "$p = 'cargo-tracker'.ToLower(); ($p -replace '[^a-z0-9]+', '-') -replace '^-+|-+$', ''"') do set "IMAGE_NAME=%%i"

    set "FULL_IMAGE_NAME=!REGISTRY_URL!/!ECR_REPO!:!IMAGE_TAG!"

    echo.
    echo === Logging in to AWS ECR ===
    aws ecr get-login-password --region !AWS_REGION! | docker login --username AWS --password-stdin !REGISTRY_URL!
    if !ERRORLEVEL! neq 0 (
        echo ECR login failed
        exit /b 1
    )

    echo.
    echo === Checking ECR repository ===
    aws ecr describe-repositories --repository-names !ECR_REPO! --region !AWS_REGION! >nul 2>&1
    if !ERRORLEVEL! neq 0 (
        echo Creating ECR repository: !ECR_REPO!
        aws ecr create-repository --repository-name !ECR_REPO! --region !AWS_REGION!
    ) else (
        echo ECR repository already exists: !ECR_REPO!
    )

    echo.
    echo === Building Docker image ===
    echo Image: !FULL_IMAGE_NAME!
    docker build -f Dockerfile -t "!FULL_IMAGE_NAME!" .
    if !ERRORLEVEL! neq 0 (
        echo Docker build failed
        exit /b 1
    )

    echo.
    echo === Pushing Docker image ===
    docker push "!FULL_IMAGE_NAME!"
    if !ERRORLEVEL! neq 0 (
        echo Docker push failed
        exit /b 1
    )

    echo.
    echo === Success ===
    echo Image pushed to: !FULL_IMAGE_NAME!

) else if ("!PLATFORM_CHOICE!"=="2") (
    set /p DOCKER_USERNAME="Enter Docker Hub username: "
    set /p DOCKER_PASSWORD="Enter Docker Hub password: "
    set /p IMAGE_TAG="Enter image tag (or press Enter for 'latest'): "

    if "!IMAGE_TAG!"=="" set "IMAGE_TAG=latest"

    REM Sanitize image name using PowerShell
    for /f %%i in ('powershell -command "$p = 'cargo-tracker'.ToLower(); ($p -replace '[^a-z0-9]+', '-') -replace '^-+|-+$', ''"') do set "IMAGE_NAME=%%i"

    set "FULL_IMAGE_NAME=!DOCKER_USERNAME!/!IMAGE_NAME!:!IMAGE_TAG!"

    echo.
    echo === Logging in to Docker Hub ===
    echo !DOCKER_PASSWORD! | docker login --username !DOCKER_USERNAME! --password-stdin
    if !ERRORLEVEL! neq 0 (
        echo Docker Hub login failed
        exit /b 1
    )

    echo.
    echo === Building Docker image ===
    echo Image: !FULL_IMAGE_NAME!
    docker build -f Dockerfile -t "!FULL_IMAGE_NAME!" .
    if !ERRORLEVEL! neq 0 (
        echo Docker build failed
        exit /b 1
    )

    echo.
    echo === Pushing Docker image ===
    docker push "!FULL_IMAGE_NAME!"
    if !ERRORLEVEL! neq 0 (
        echo Docker push failed
        exit /b 1
    )

    echo.
    echo === Success ===
    echo Image pushed to: !FULL_IMAGE_NAME!

) else (
    echo ERROR: Invalid choice. Please select 1 or 2.
    exit /b 1
)

endlocal
