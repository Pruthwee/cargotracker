@echo off
setlocal enabledelayedexpansion

set "PROJECT_NAME=cargo-tracker"
set "DOCKERFILE_PATH=Dockerfile"
set "BUILD_CONTEXT=."

echo Cargo Tracker Docker image build and push
set /p IMAGE_TAG="Enter image tag (default latest): "
if "!IMAGE_TAG!"=="" set "IMAGE_TAG=latest"
for /f %%A in ('powershell -NoProfile -Command "$s='!IMAGE_TAG!'.ToLower() -replace '[^a-z0-9._-]+','-'; $s=$s.Trim('-'); if ([string]::IsNullOrWhiteSpace($s)) { 'latest' } else { $s }"') do set "IMAGE_TAG=%%A"
for /f %%A in ('powershell -NoProfile -Command "$s='!PROJECT_NAME!'.ToLower() -replace '[^a-z0-9]+','-'; $s=$s.Trim('-'); $s"') do set "IMAGE_NAME=%%A"
if "!IMAGE_NAME!"=="" (
  echo Unable to derive a valid image name
  exit /b 1
)

echo Select container registry
echo 1. Azure Container Registry ACR
echo 2. Docker Hub
set /p REGISTRY_CHOICE="Enter choice [1-2]: "

if "!REGISTRY_CHOICE!"=="1" (
  set /p ACR_NAME="Enter Azure Container Registry name without azurecr.io: "
  if "!ACR_NAME!"=="" (
    echo ACR name is required
    exit /b 1
  )
  echo Logging in to ACR !ACR_NAME!
  az acr login --name !ACR_NAME!
  if !ERRORLEVEL! neq 0 (echo ACR login failed & exit /b 1)
  set "REGISTRY_SERVER=!ACR_NAME!.azurecr.io"
  set "FULL_IMAGE_NAME=!REGISTRY_SERVER!/!IMAGE_NAME!:!IMAGE_TAG!"
) else if "!REGISTRY_CHOICE!"=="2" (
  set /p DOCKER_USERNAME="Enter Docker Hub username: "
  set /p DOCKER_PASSWORD="Enter Docker Hub password or access token: "
  if "!DOCKER_USERNAME!"=="" (
    echo Docker Hub username is required
    exit /b 1
  )
  if "!DOCKER_PASSWORD!"=="" (
    echo Docker Hub password is required
    exit /b 1
  )
  echo !DOCKER_PASSWORD! | docker login --username !DOCKER_USERNAME! --password-stdin
  if !ERRORLEVEL! neq 0 (echo Docker Hub login failed & exit /b 1)
  set "FULL_IMAGE_NAME=!DOCKER_USERNAME!/!IMAGE_NAME!:!IMAGE_TAG!"
) else (
  echo Invalid registry choice
  exit /b 1
)

echo Building Docker image !FULL_IMAGE_NAME!
docker build -f !DOCKERFILE_PATH! -t !FULL_IMAGE_NAME! !BUILD_CONTEXT!
if !ERRORLEVEL! neq 0 (echo Docker build failed & exit /b 1)

echo Pushing Docker image !FULL_IMAGE_NAME!
docker push !FULL_IMAGE_NAME!
if !ERRORLEVEL! neq 0 (echo Docker push failed & exit /b 1)

echo Image pushed successfully !FULL_IMAGE_NAME!
endlocal
