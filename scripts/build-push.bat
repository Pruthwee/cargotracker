@echo off
setlocal enabledelayedexpansion

echo === Cargo Tracker Build and Push ===

set "PROJECT_NAME=Cargo-MONO-1"

:: Sanitize image name
for /f "delims=" %%i in ('powershell -command "$n = '%PROJECT_NAME%'.ToLower(); ($n -replace '[^a-z0-9]+', '-') -replace '^-+|-+$', ''") do set "IMAGE_NAME=%%i"

:: Ask for AWS region
set /p AWS_REGION="Enter AWS region (e.g., us-east-1): "
if "!AWS_REGION!"=="" (
  echo AWS region is required.
  exit /b 1
)

:: Ask for ECR repository name
set /p ECR_REPO="Enter ECR repository name (default: !IMAGE_NAME!): "
if "!ECR_REPO!"=="" set "ECR_REPO=!IMAGE_NAME!"

:: Ask for image tag
set /p IMAGE_TAG="Enter image tag (default: latest): "
if "!IMAGE_TAG!"=="" set "IMAGE_TAG=latest"

:: Get AWS account ID
for /f "delims=" %%i in ('aws sts get-caller-identity --query Account --output text --region !AWS_REGION!') do set "AWS_ACCOUNT_ID=%%i"
if "!AWS_ACCOUNT_ID!"=="" (
  echo Could not retrieve AWS account ID.
  exit /b 1
)

set "REGISTRY_URL=!AWS_ACCOUNT_ID!.dkr.ecr.!AWS_REGION!.amazonaws.com"
set "FULL_IMAGE_NAME=!REGISTRY_URL!/!ECR_REPO!:!IMAGE_TAG!"

echo.
echo Project: %PROJECT_NAME%
echo Image: !FULL_IMAGE_NAME!
echo Registry: !REGISTRY_URL!
echo.

:: Login to ECR
echo Logging in to Amazon ECR...
call aws ecr get-login-password --region !AWS_REGION! | docker login --username AWS --password-stdin !REGISTRY_URL!
if !ERRORLEVEL! neq 0 (
  echo ECR login failed
  exit /b 1
)

:: Create ECR repository if it doesn't exist
echo Checking if ECR repository exists...
aws ecr describe-repositories --repository-names !ECR_REPO! --region !AWS_REGION! >nul 2>&1
if !ERRORLEVEL! neq 0 (
  echo Creating ECR repository: !ECR_REPO!
  aws ecr create-repository --repository-name !ECR_REPO! --region !AWS_REGION!
)

:: Build the Docker image
echo Building Docker image...
docker build -f Dockerfile -t "!FULL_IMAGE_NAME!" .
if !ERRORLEVEL! neq 0 (
  echo Docker build failed
  exit /b 1
)

:: Push the Docker image
echo Pushing Docker image...
docker push "!FULL_IMAGE_NAME!"
if !ERRORLEVEL! neq 0 (
  echo Docker push failed
  exit /b 1
)

echo.
echo === Build and push complete ===
echo Image: !FULL_IMAGE_NAME!

endlocal
