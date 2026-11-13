@echo off
setlocal enabledelayedexpansion

echo === Cargo Tracker - Build and Push to AWS ECR ===

set "PROJECT_NAME=cargo-tracker"
set "IMAGE_NAME=cargo-tracker"

echo.
echo Target platform: AWS ECR

set /p AWS_REGION="Enter AWS region (e.g., us-east-1): "
if "!AWS_REGION!"=="" (
  echo AWS region is required.
  exit /b 1
)

set /p ECR_REPO="Enter ECR repository name (default: !IMAGE_NAME!): "
if "!ECR_REPO!"=="" set "ECR_REPO=!IMAGE_NAME!"

set /p IMAGE_TAG="Enter image tag (default: latest): "
if "!IMAGE_TAG!"=="" set "IMAGE_TAG=latest"

set /p AWS_ACCOUNT_ID="Enter AWS account ID: "
if "!AWS_ACCOUNT_ID!"=="" (
  echo AWS account ID is required.
  exit /b 1
)

set "REGISTRY_URL=!AWS_ACCOUNT_ID!.dkr.ecr.!AWS_REGION!.amazonaws.com"
set "FULL_IMAGE_NAME=!REGISTRY_URL!/!ECR_REPO!:!IMAGE_TAG!"

echo.
echo Building image: !FULL_IMAGE_NAME!
echo Platform: linux/amd64
echo.

echo === Building Docker image ===
docker build -f Dockerfile -t "!FULL_IMAGE_NAME!" --platform linux/amd64 .
if !ERRORLEVEL! neq 0 (
  echo Docker build failed
  exit /b 1
)

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
  echo Creating ECR repository...
  aws ecr create-repository --repository-name !ECR_REPO! --region !AWS_REGION!
  if !ERRORLEVEL! neq 0 (
    echo Failed to create ECR repository
    exit /b 1
  )
)

echo.
echo === Pushing Docker image ===
docker push "!FULL_IMAGE_NAME!"
if !ERRORLEVEL! neq 0 (
  echo Docker push failed
  exit /b 1
)

echo.
echo === Build and push complete ===
echo Image: !FULL_IMAGE_NAME!
echo Registry: !REGISTRY_URL!
echo Repository: !ECR_REPO!
echo Tag: !IMAGE_TAG!

endlocal
