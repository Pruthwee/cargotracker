#!/bin/bash
set -e
set -o pipefail

PROJECT_NAME="cargo-tracker"
DOCKERFILE_PATH="Dockerfile"
BUILD_CONTEXT="."

IMAGE_NAME=$(echo "$PROJECT_NAME" | tr '[:upper:]' '[:lower:]' | tr -cs 'a-z0-9' '-' | sed 's/^-*//;s/-*$//')
if [ -z "$IMAGE_NAME" ]; then
  echo "Unable to derive a valid Docker image name."
  exit 1
fi

read -r -p "Enter image tag [latest]: " IMAGE_TAG
IMAGE_TAG=$(echo "${IMAGE_TAG:-latest}" | tr '[:upper:]' '[:lower:]' | tr -cs 'a-z0-9._-' '-' | sed 's/^-*//;s/-*$//')
if [ -z "$IMAGE_TAG" ]; then
  IMAGE_TAG="latest"
fi

echo "Select container registry:"
echo "1. Azure Container Registry (ACR)"
echo "2. Docker Hub"
read -r -p "Enter choice [1-2]: " REGISTRY_CHOICE

if [ "$REGISTRY_CHOICE" = "1" ]; then
  read -r -p "Enter ACR name (without .azurecr.io): " ACR_NAME
  if [ -z "$ACR_NAME" ]; then
    echo "ACR name is required."
    exit 1
  fi
  REGISTRY_SERVER="${ACR_NAME}.azurecr.io"
  FULL_IMAGE_NAME="${REGISTRY_SERVER}/${IMAGE_NAME}:${IMAGE_TAG}"
  echo "Logging in to Azure Container Registry ${ACR_NAME}..."
  az acr login --name "$ACR_NAME"
elif [ "$REGISTRY_CHOICE" = "2" ]; then
  read -r -p "Enter Docker Hub username: " DOCKER_USERNAME
  read -r -s -p "Enter Docker Hub password or access token: " DOCKER_PASSWORD
  echo
  if [ -z "$DOCKER_USERNAME" ] || [ -z "$DOCKER_PASSWORD" ]; then
    echo "Docker Hub username and password/token are required."
    exit 1
  fi
  FULL_IMAGE_NAME="${DOCKER_USERNAME}/${IMAGE_NAME}:${IMAGE_TAG}"
  echo "$DOCKER_PASSWORD" | docker login --username "$DOCKER_USERNAME" --password-stdin
else
  echo "Invalid registry choice."
  exit 1
fi

read -r -p "Enter PostgreSQL JDBC URL for build-time web.xml filtering [jdbc:postgresql://host.docker.internal:5432/postgres]: " POSTGRESQL_JDBC_URL
read -r -p "Enter PostgreSQL username for build-time filtering [cargotracker]: " POSTGRESQL_USERNAME
read -r -s -p "Enter PostgreSQL password for build-time filtering [cargotracker]: " POSTGRESQL_PASSWORD
echo
POSTGRESQL_JDBC_URL=${POSTGRESQL_JDBC_URL:-jdbc:postgresql://host.docker.internal:5432/postgres}
POSTGRESQL_USERNAME=${POSTGRESQL_USERNAME:-cargotracker}
POSTGRESQL_PASSWORD=${POSTGRESQL_PASSWORD:-cargotracker}

echo "Building Docker image ${FULL_IMAGE_NAME}..."
docker build -f "$DOCKERFILE_PATH" -t "$FULL_IMAGE_NAME" \
  --build-arg POSTGRESQL_JDBC_URL="$POSTGRESQL_JDBC_URL" \
  --build-arg POSTGRESQL_USERNAME="$POSTGRESQL_USERNAME" \
  --build-arg POSTGRESQL_PASSWORD="$POSTGRESQL_PASSWORD" \
  "$BUILD_CONTEXT"

echo "Pushing Docker image ${FULL_IMAGE_NAME}..."
docker push "$FULL_IMAGE_NAME"

echo "Image pushed successfully: ${FULL_IMAGE_NAME}"
