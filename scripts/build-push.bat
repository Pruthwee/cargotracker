@echo off
setlocal enabledelayedexpansion

set "PROJECT_NAME=cargo-tracker"
set "DOCKERFILE_PATH=Dockerfile"
set "BUILD_CONTEXT=."

for /f "delims=" %%A in ('powershell -NoProfile -Command "$name='%PROJECT_NAME%'.ToLower() -replace '[^a-z0-9]+','-'; $name=$name.Trim('-'); Write-Output $name"') do set "IMAGE_NAME=%%A"
if "!IMAGE_NAME!"=="" (
  echo Unable to derive a valid Docker image name
  exit /b 1
)

set /p IMAGE_TAG="Enter image tag [latest]: "
if "!IMAGE_TAG!"=="" set "IMAGE_TAG=latest"
for /f "delims=" %%A in ('powershell -NoProfile -Command "$tag='!IMAGE_TAG!'.ToLower() -replace '[^a-z0-9._-]+','-'; $tag=$tag.Trim('-'); if ([string]::IsNullOrWhiteSpace($tag)) { $tag='latest' }; Write-Output $tag"') do set "IMAGE_TAG=%%A"

echo Select container registry
echo 1 Azure Container Registry ACR
echo 2 Docker Hub
set /p REGISTRY_CHOICE="Enter choice [1-2]: "

if "!REGISTRY_CHOICE!"=="1" (
  set /p ACR_NAME="Enter ACR name without azurecr io suffix: "
  if "!ACR_NAME!"=="" (
    echo ACR name is required
    exit /b 1
  )
  set "REGISTRY_SERVER=!ACR_NAME!.azurecr.io"
  set "FULL_IMAGE_NAME=!REGISTRY_SERVER!/!IMAGE_NAME!:!IMAGE_TAG!"
  echo Logging in to Azure Container Registry
  az acr login --name !ACR_NAME!
  if !ERRORLEVEL! neq 0 (echo ACR login failed & exit /b 1)
) else if "!REGISTRY_CHOICE!"=="2" (
  set /p DOCKER_USERNAME="Enter Docker Hub username: "
  set /p DOCKER_PASSWORD="Enter Docker Hub password or access token: "
  if "!DOCKER_USERNAME!"=="" (
    echo Docker Hub username is required
    exit /b 1
  )
  if "!DOCKER_PASSWORD!"=="" (
    echo Docker Hub password or token is required
    exit /b 1
  )
  set "FULL_IMAGE_NAME=!DOCKER_USERNAME!/!IMAGE_NAME!:!IMAGE_TAG!"
  echo !DOCKER_PASSWORD! | docker login --username !DOCKER_USERNAME! --password-stdin
  if !ERRORLEVEL! neq 0 (echo Docker Hub login failed & exit /b 1)
) else (
  echo Invalid registry choice
  exit /b 1
)

set /p POSTGRESQL_JDBC_URL="Enter PostgreSQL JDBC URL for build-time web xml filtering [jdbc:postgresql://host.docker.internal:5432/postgres]: "
set /p POSTGRESQL_USERNAME="Enter PostgreSQL username for build-time filtering [cargotracker]: "
set /p POSTGRESQL_PASSWORD="Enter PostgreSQL password for build-time filtering [cargotracker]: "
if "!POSTGRESQL_JDBC_URL!"=="" set "POSTGRESQL_JDBC_URL=jdbc:postgresql://host.docker.internal:5432/postgres"
if "!POSTGRESQL_USERNAME!"=="" set "POSTGRESQL_USERNAME=cargotracker"
if "!POSTGRESQL_PASSWORD!"=="" set "POSTGRESQL_PASSWORD=cargotracker"

echo Building Docker image !FULL_IMAGE_NAME!
docker build -f !DOCKERFILE_PATH! -t !FULL_IMAGE_NAME! --build-arg POSTGRESQL_JDBC_URL="!POSTGRESQL_JDBC_URL!" --build-arg POSTGRESQL_USERNAME="!POSTGRESQL_USERNAME!" --build-arg POSTGRESQL_PASSWORD="!POSTGRESQL_PASSWORD!" !BUILD_CONTEXT!
if !ERRORLEVEL! neq 0 (echo Docker build failed & exit /b 1)

echo Pushing Docker image !FULL_IMAGE_NAME!
docker push !FULL_IMAGE_NAME!
if !ERRORLEVEL! neq 0 (echo Docker push failed & exit /b 1)

echo Image pushed successfully !FULL_IMAGE_NAME!
endlocal
