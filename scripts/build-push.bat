@echo off
setlocal enabledelayedexpansion

:: ============================================================
:: build-push.bat - Build and push Docker image for cargo-tracker
:: ============================================================

set PROJECT_NAME=cargo-tracker
set DOCKERFILE_PATH=Dockerfile

echo ==============================================
echo   cargo-tracker - Docker Build ^& Push Script
echo ==============================================
echo.

:: ---- Registry selection ----
echo Select container registry:
echo   1. AWS ECR (Elastic Container Registry)
echo   2. Docker Hub
echo.
set /p REGISTRY_CHOICE="Enter choice [1 or 2]: "

:: ---- Tag input ----
set /p RAW_TAG="Enter image tag (press Enter for 'latest'): "

:: Sanitize image name (lowercase, replace invalid chars with hyphen)
set IMAGE_NAME=%PROJECT_NAME%
for /f "delims=" %%i in ('powershell -Command "('%IMAGE_NAME%').ToLower() -replace '[^a-z0-9]','-' -replace '^-+','' -replace '-+$',''"') do set IMAGE_NAME=%%i

:: Sanitize tag
if "!RAW_TAG!"=="" (
  set IMAGE_TAG=latest
) else (
  for /f "delims=" %%i in ('powershell -Command "('!RAW_TAG!').ToLower() -replace '[^a-z0-9._-]','-' -replace '^-+','' -replace '-+$',''"') do set IMAGE_TAG=%%i
  if "!IMAGE_TAG!"=="" set IMAGE_TAG=latest
)

echo.
echo Image name : !IMAGE_NAME!
echo Image tag  : !IMAGE_TAG!
echo.

:: ============================================================
:: AWS ECR
:: ============================================================
if "!REGISTRY_CHOICE!"=="1" (
  set /p AWS_REGION="Enter AWS Region (e.g. us-east-1): "
  set /p AWS_ACCOUNT_ID="Enter AWS Account ID: "
  set /p ECR_REPO_INPUT="Enter ECR repository name [!IMAGE_NAME!]: "
  if "!ECR_REPO_INPUT!"=="" (
    set ECR_REPO=!IMAGE_NAME!
  ) else (
    set ECR_REPO=!ECR_REPO_INPUT!
  )

  set REGISTRY_URL=!AWS_ACCOUNT_ID!.dkr.ecr.!AWS_REGION!.amazonaws.com
  set FULL_IMAGE_NAME=!REGISTRY_URL!/!ECR_REPO!:!IMAGE_TAG!

  echo.
  echo Authenticating with AWS ECR...
  aws ecr get-login-password --region !AWS_REGION! | docker login --username AWS --password-stdin !REGISTRY_URL!
  if !ERRORLEVEL! neq 0 (
    echo ERROR: ECR login failed.
    exit /b 1
  )

  echo Checking/creating ECR repository: !ECR_REPO! ...
  aws ecr describe-repositories --repository-names !ECR_REPO! --region !AWS_REGION! >nul 2>&1
  if !ERRORLEVEL! neq 0 (
    echo Creating ECR repository...
    aws ecr create-repository --repository-name !ECR_REPO! --region !AWS_REGION!
    if !ERRORLEVEL! neq 0 (
      echo ERROR: Failed to create ECR repository.
      exit /b 1
    )
  )

:: ============================================================
:: Docker Hub
:: ============================================================
) else if "!REGISTRY_CHOICE!"=="2" (
  set /p DOCKER_USERNAME="Enter Docker Hub username: "
  set /p DOCKER_PASSWORD="Enter Docker Hub password/token: "
  set /p DOCKER_NAMESPACE_INPUT="Enter Docker Hub namespace/org [!DOCKER_USERNAME!]: "
  if "!DOCKER_NAMESPACE_INPUT!"=="" (
    set DOCKER_NAMESPACE=!DOCKER_USERNAME!
  ) else (
    set DOCKER_NAMESPACE=!DOCKER_NAMESPACE_INPUT!
  )

  set REGISTRY_URL=docker.io
  set FULL_IMAGE_NAME=!DOCKER_NAMESPACE!/!IMAGE_NAME!:!IMAGE_TAG!

  echo.
  echo Authenticating with Docker Hub...
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
echo Full image: !FULL_IMAGE_NAME!
echo.

:: ---- Build ----
echo Building Docker image...
docker build -f !DOCKERFILE_PATH! -t !FULL_IMAGE_NAME! .
if !ERRORLEVEL! neq 0 (
  echo ERROR: Docker build failed.
  exit /b 1
)
echo Build successful.

:: ---- Tag as latest (if not already) ----
if not "!IMAGE_TAG!"=="latest" (
  for /f "tokens=1 delims=:" %%a in ("!FULL_IMAGE_NAME!") do set BASE_IMAGE=%%a
  set LATEST_IMAGE=!FULL_IMAGE_NAME::!IMAGE_TAG!=:latest!
  docker tag !FULL_IMAGE_NAME! !LATEST_IMAGE!
  echo Tagged as: !LATEST_IMAGE!
)

:: ---- Push ----
echo.
echo Pushing image: !FULL_IMAGE_NAME! ...
docker push !FULL_IMAGE_NAME!
if !ERRORLEVEL! neq 0 (
  echo ERROR: Docker push failed.
  exit /b 1
)

if not "!IMAGE_TAG!"=="latest" (
  echo Pushing image: !LATEST_IMAGE! ...
  docker push !LATEST_IMAGE!
  if !ERRORLEVEL! neq 0 (
    echo ERROR: Docker push of latest tag failed.
    exit /b 1
  )
)

echo.
echo ==============================================
echo   Image pushed successfully!
echo   !FULL_IMAGE_NAME!
echo ==============================================

endlocal
