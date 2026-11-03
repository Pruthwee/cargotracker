@echo off
setlocal enabledelayedexpansion

echo === Cargo Tracker Build and Push Script ===
echo.

set PROJECT_NAME=cargo-tracker
set IMAGE_NAME=cargo-tracker

echo Select target registry:
echo 1) AWS ECR
echo 2) Docker Hub
set /p REGISTRY_CHOICE="Enter choice (1 or 2): "

if "!REGISTRY_CHOICE!"=="1" (
  echo.
  echo === AWS ECR ===
  set /p AWS_REGION="Enter AWS region (e.g., us-east-1): "
  set /p ECR_REPO="Enter ECR repository name: "
  set REGISTRY_URL=!AWS_REGION!.dkr.ecr.!AWS_REGION!.amazonaws.com
  set FULL_REGISTRY=!REGISTRY_URL!
) else if "!REGISTRY_CHOICE!"=="2" (
  echo.
  echo === Docker Hub ===
  set /p DOCKER_USERNAME="Enter Docker Hub username: "
  set FULL_REGISTRY=!DOCKER_USERNAME!
) else (
  echo Invalid choice. Exiting.
  exit /b 1
)

set /p IMAGE_TAG="Enter image tag (press Enter for 'latest'): "
if "!IMAGE_TAG!"=="" (
  set IMAGE_TAG=latest
)

REM Sanitize tag using PowerShell
for /f "delims=" %%i in ('powershell -Command "( '!IMAGE_TAG!' -replace '[^a-z0-9-.]', '-' ).Trim('-')"') do set IMAGE_TAG=%%i

set FULL_IMAGE_NAME=!FULL_REGISTRY!/!IMAGE_NAME!:!IMAGE_TAG!

echo.
echo === Build Configuration ===
echo Project:        !PROJECT_NAME!
echo Image name:     !IMAGE_NAME!
echo Full image:     !FULL_IMAGE_NAME!
echo Build context:  .
echo.

echo === Building Docker image ===
docker build -f Dockerfile -t "!FULL_IMAGE_NAME!" .
if !ERRORLEVEL! neq 0 (
  echo Docker build failed
  exit /b 1
)
echo Docker build completed successfully.
echo.

if "!REGISTRY_CHOICE!"=="1" (
  echo === Logging in to AWS ECR ===
  call aws ecr get-login-password --region !AWS_REGION! | docker login --username AWS --password-stdin !REGISTRY_URL!
  if !ERRORLEVEL! neq 0 (
    echo ECR login failed
    exit /b 1
  )
  echo ECR login successful.

  echo === Creating ECR repository if it does not exist ===
  call aws ecr describe-repositories --repository-names !ECR_REPO! --region !AWS_REGION! >nul 2>&1
  if !ERRORLEVEL! neq 0 (
    echo Creating ECR repository...
    call aws ecr create-repository --repository-name !ECR_REPO! --region !AWS_REGION!
  )
  echo ECR repository ready.
) else if "!REGISTRY_CHOICE!"=="2" (
  echo === Logging in to Docker Hub ===
  set /p DOCKER_PASSWORD="Enter Docker Hub password: "
  echo !DOCKER_PASSWORD! | docker login --username !DOCKER_USERNAME! --password-stdin
  if !ERRORLEVEL! neq 0 (
    echo Docker Hub login failed
    exit /b 1
  )
  echo Docker Hub login successful.
)

echo.
echo === Pushing Docker image ===
docker push "!FULL_IMAGE_NAME!"
if !ERRORLEVEL! neq 0 (
  echo Docker push failed
  exit /b 1
)
echo Docker push completed successfully.
echo.
echo Image pushed: !FULL_IMAGE_NAME!

endlocal
