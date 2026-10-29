@echo off & setlocal enabledelayedexpansion
rem Builds the cargotrackerM-Mono image and pushes it to AWS ECR or Docker Hub.
rem Run it from the repository root: scripts\build-push.bat

set "PROJECT_NAME=cargotrackerM-Mono"
powershell -NoProfile -Command "$n='!PROJECT_NAME!'.ToLower() -replace '[^a-z0-9]+','-'; $n.Trim('-')" > "%TEMP%\cz_image_name.tmp"
set /p IMAGE_NAME=<"%TEMP%\cz_image_name.tmp"
del "%TEMP%\cz_image_name.tmp" >nul 2>&1

where docker >nul 2>&1
if !ERRORLEVEL! neq 0 (echo ERROR docker is not installed or not in PATH & exit /b 1)

echo Select the registry
echo   1. AWS ECR (target platform)
echo   2. Docker Hub
set /p REGISTRY_CHOICE="Enter 1 or 2: "

if "!REGISTRY_CHOICE!"=="1" (
  where aws >nul 2>&1
  if !ERRORLEVEL! neq 0 (echo ERROR the AWS CLI is not installed or not in PATH & exit /b 1)
  set /p AWS_REGION="Enter the AWS region (for example us-east-1): "
  set /p AWS_ACCOUNT_ID="Enter the AWS account ID: "
  set /p ECR_REPO="Enter the ECR repository name (press Enter for the default): "
  if "!ECR_REPO!"=="" set "ECR_REPO=!IMAGE_NAME!"
  if "!AWS_REGION!"=="" (echo ERROR the AWS region is required & exit /b 1)
  if "!AWS_ACCOUNT_ID!"=="" (echo ERROR the AWS account ID is required & exit /b 1)
  set "REGISTRY_URL=!AWS_ACCOUNT_ID!.dkr.ecr.!AWS_REGION!.amazonaws.com"
  set "REPO_PATH=!ECR_REPO!"
) else if "!REGISTRY_CHOICE!"=="2" (
  set /p DOCKER_USERNAME="Enter your Docker Hub username: "
  set /p DOCKER_PASSWORD="Enter your Docker Hub password or access token: "
  set /p DOCKER_REPO="Enter the repository name (press Enter for the default): "
  if "!DOCKER_REPO!"=="" set "DOCKER_REPO=!IMAGE_NAME!"
  if "!DOCKER_USERNAME!"=="" (echo ERROR the Docker Hub username is required & exit /b 1)
  if "!DOCKER_PASSWORD!"=="" (echo ERROR the Docker Hub password is required & exit /b 1)
  set "REGISTRY_URL=docker.io"
  set "REPO_PATH=!DOCKER_USERNAME!/!DOCKER_REPO!"
) else (
  echo ERROR invalid choice, enter 1 or 2
  exit /b 1
)

set "IMAGE_TAG="
set /p IMAGE_TAG="Enter IMAGE_TAG (press Enter for latest): "
if "!IMAGE_TAG!"=="" set "IMAGE_TAG=latest"
powershell -NoProfile -Command "$t='!IMAGE_TAG!'.ToLower() -replace '[^a-z0-9._-]+','-'; $t = $t.Trim('-'); if ($t -eq '') { $t = 'latest' }; $t" > "%TEMP%\cz_image_tag.tmp"
set /p IMAGE_TAG=<"%TEMP%\cz_image_tag.tmp"
del "%TEMP%\cz_image_tag.tmp" >nul 2>&1

set "FULL_IMAGE_NAME=!REGISTRY_URL!/!REPO_PATH!:!IMAGE_TAG!"

echo Building the image !FULL_IMAGE_NAME!
docker build -f Dockerfile -t !FULL_IMAGE_NAME! .
if !ERRORLEVEL! neq 0 (echo Docker build failed & exit /b 1)
echo Build finished

if "!REGISTRY_CHOICE!"=="1" (
  echo Logging in to AWS ECR
  aws ecr get-login-password --region !AWS_REGION! | docker login --username AWS --password-stdin !REGISTRY_URL!
  if !ERRORLEVEL! neq 0 (echo ECR login failed & exit /b 1)
  echo Checking the ECR repository !ECR_REPO!
  aws ecr describe-repositories --repository-names !ECR_REPO! --region !AWS_REGION! >nul 2>&1
  if !ERRORLEVEL! neq 0 (echo Creating ECR repository... & aws ecr create-repository --repository-name !ECR_REPO! --region !AWS_REGION!)
) else (
  echo Logging in to Docker Hub
  echo !DOCKER_PASSWORD! | docker login --username !DOCKER_USERNAME! --password-stdin
  if !ERRORLEVEL! neq 0 (echo Docker Hub login failed & exit /b 1)
)

echo Pushing !FULL_IMAGE_NAME!
docker push !FULL_IMAGE_NAME!
if !ERRORLEVEL! neq 0 (echo Docker push failed & exit /b 1)

echo Done. Pushed image !FULL_IMAGE_NAME!
echo Use this image URI when running scripts\deploy-image.bat
endlocal
