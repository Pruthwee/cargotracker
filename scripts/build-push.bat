@echo off
setlocal enabledelayedexpansion

REM Build and push script for cargo-tracker on AWS ECR or Docker Hub
REM Usage: scripts\build-push.bat

set PROJECT_NAME=cargotrackerMMono
REM Sanitize IMAGE_NAME: lowercase, non-alphanumeric to hyphen, trim hyphens
for /f "delims=" %%i in ('powershell -command "$n='cargotrackerMMono'.ToLower(); $n -creplace '[^a-z0-9]+','-'; $n.Trim('-')"') do set IMAGE_NAME=%%i

echo === Cargo Tracker Build and Push ===
echo Project name: %PROJECT_NAME%
echo Image name: %IMAGE_NAME%
echo.

echo Choose registry:
echo 1. AWS ECR
echo 2. Docker Hub
set /p REGISTRY_CHOICE="Enter choice (1 or 2): "

if "%REGISTRY_CHOICE%"=="1" (
  set /p AWS_REGION="Enter AWS region (e.g., us-east-1): "
  set /p REGISTRY_URL="Enter ECR registry URL (e.g., 123456789012.dkr.ecr.us-east-1.amazonaws.com): "
  set /p ECR_REPO="Enter ECR repository name (e.g., cargo-tracker): "
  set /p IMAGE_TAG="Enter image tag (or press Enter for latest): "

  if "!IMAGE_TAG!"=="" (
    set IMAGE_TAG=latest
  )

  set FULL_IMAGE_NAME=!REGISTRY_URL!/!ECR_REPO!:!IMAGE_TAG!

  echo.
  echo === Configuration ===
  echo Registry: AWS ECR
  echo Region: !AWS_REGION!
  echo Registry URL: !REGISTRY_URL!
  echo Repository: !ECR_REPO!
  echo Image tag: !IMAGE_TAG!
  echo Full image name: !FULL_IMAGE_NAME!
  echo.

  echo === Logging in to AWS ECR ===
  call aws ecr get-login-password --region !AWS_REGION! | docker login --username AWS --password-stdin !REGISTRY_URL!
  if !ERRORLEVEL! neq 0 (
    echo ECR login failed
    exit /b 1
  )

  echo.
  echo === Creating ECR repository if it does not exist ===
  call aws ecr describe-repositories --repository-names !ECR_REPO! --region !AWS_REGION! >nul 2>&1
  if !ERRORLEVEL! neq 0 (
    echo Creating ECR repository...
    call aws ecr create-repository --repository-name !ECR_REPO! --region !AWS_REGION!
  )

  echo.
  echo === Building Docker image ===
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
  echo === Build and push complete ===
  echo Image: !FULL_IMAGE_NAME!

) else if "%REGISTRY_CHOICE%"=="2" (
  set /p DOCKER_USERNAME="Enter Docker Hub username: "
  call :read_password DOCKER_PASSWORD
  set /p IMAGE_TAG="Enter image tag (or press Enter for latest): "

  if "!IMAGE_TAG!"=="" (
    set IMAGE_TAG=latest
  )

  set FULL_IMAGE_NAME=!DOCKER_USERNAME!/!IMAGE_NAME!:!IMAGE_TAG!

  echo.
  echo === Configuration ===
  echo Registry: Docker Hub
  echo Username: !DOCKER_USERNAME!
  echo Image tag: !IMAGE_TAG!
  echo Full image name: !FULL_IMAGE_NAME!
  echo.

  echo === Logging in to Docker Hub ===
  echo !DOCKER_PASSWORD! | docker login --username !DOCKER_USERNAME! --password-stdin
  if !ERRORLEVEL! neq 0 (
    echo Docker Hub login failed
    exit /b 1
  )

  echo.
  echo === Building Docker image ===
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
  echo === Build and push complete ===
  echo Image: !FULL_IMAGE_NAME!

) else (
  echo Invalid choice. Exiting.
  exit /b 1
)

exit /b 0

:read_password
set /p %1="Enter Docker Hub password: "
exit /b 0
