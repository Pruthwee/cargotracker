@echo off
setlocal enabledelayedexpansion

set PROJECT_NAME=cargo-tracker
set IMAGE_NAME=cargo-tracker

echo === Cargo Tracker Build and Push ===
echo Project name: %PROJECT_NAME%
echo Image name: %IMAGE_NAME%

echo.
echo Select target platform:
echo 1. AWS ECR
echo 2. Docker Hub
set /p PLATFORM_CHOICE="Enter choice (1 or 2): "

if "%PLATFORM_CHOICE%"=="1" (
  echo.
  echo === AWS ECR Configuration ===
  set /p AWS_REGION="Enter AWS region: "
  set /p AWS_ACCOUNT_ID="Enter AWS account ID: "
  set /p ECR_REPO="Enter ECR repository name (press Enter for default '%IMAGE_NAME%'): "
  if "!ECR_REPO!"=="" set ECR_REPO=%IMAGE_NAME%
  set /p IMAGE_TAG="Enter image tag (press Enter for 'latest'): "
  if "!IMAGE_TAG!"=="" set IMAGE_TAG=latest

  set REGISTRY_URL=!AWS_ACCOUNT_ID!.dkr.ecr.!AWS_REGION!.amazonaws.com
  set FULL_IMAGE_NAME=!REGISTRY_URL!/!ECR_REPO!:!IMAGE_TAG!

  echo.
  echo === Logging in to AWS ECR ===
  aws ecr get-login-password --region !AWS_REGION! | docker login --username AWS --password-stdin !REGISTRY_URL!
  if !ERRORLEVEL! neq 0 (
    echo ECR login failed
    exit /b 1
  )
  echo ECR login successful.

  echo.
  echo === Creating ECR repository if it does not exist ===
  aws ecr describe-repositories --repository-names !ECR_REPO! --region !AWS_REGION! >nul 2>&1
  if !ERRORLEVEL! neq 0 (
    echo Creating ECR repository...
    aws ecr create-repository --repository-name !ECR_REPO! --region !AWS_REGION!
  )
  echo ECR repository ready: !ECR_REPO!

  echo.
  echo === Building Docker image ===
  docker build -f Dockerfile -t !FULL_IMAGE_NAME! .
  if !ERRORLEVEL! neq 0 (
    echo Docker build failed
    exit /b 1
  )
  echo Docker image built: !FULL_IMAGE_NAME!

  echo.
  echo === Pushing Docker image to ECR ===
  docker push !FULL_IMAGE_NAME!
  if !ERRORLEVEL! neq 0 (
    echo Docker push failed
    exit /b 1
  )
  echo Docker image pushed to ECR: !FULL_IMAGE_NAME!

  echo.
  echo === Build and push complete ===
  echo Image URI: !FULL_IMAGE_NAME!

) else if "%PLATFORM_CHOICE%"=="2" (
  echo.
  echo === Docker Hub Configuration ===
  set /p DOCKER_USERNAME="Enter Docker Hub username: "
  set /p DOCKER_PASSWORD="Enter Docker Hub password: "
  set /p IMAGE_TAG="Enter image tag (press Enter for 'latest'): "
  if "!IMAGE_TAG!"=="" set IMAGE_TAG=latest

  set FULL_IMAGE_NAME=!DOCKER_USERNAME!/!IMAGE_NAME!:!IMAGE_TAG!

  echo.
  echo === Logging in to Docker Hub ===
  echo !DOCKER_PASSWORD! | docker login --username !DOCKER_USERNAME! --password-stdin
  if !ERRORLEVEL! neq 0 (
    echo Docker Hub login failed
    exit /b 1
  )
  echo Docker Hub login successful.

  echo.
  echo === Building Docker image ===
  docker build -f Dockerfile -t !FULL_IMAGE_NAME! .
  if !ERRORLEVEL! neq 0 (
    echo Docker build failed
    exit /b 1
  )
  echo Docker image built: !FULL_IMAGE_NAME!

  echo.
  echo === Pushing Docker image to Docker Hub ===
  docker push !FULL_IMAGE_NAME!
  if !ERRORLEVEL! neq 0 (
    echo Docker push failed
    exit /b 1
  )
  echo Docker image pushed to Docker Hub: !FULL_IMAGE_NAME!

  echo.
  echo === Build and push complete ===
  echo Image URI: !FULL_IMAGE_NAME!

) else (
  echo Invalid choice. Exiting.
  exit /b 1
)

endlocal
