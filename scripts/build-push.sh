#!/bin/bash
set -e
set -o pipefail

PROJECT_NAME="cargo-tracker"
DOCKERFILE_PATH="Dockerfile"
BUILD_CONTEXT="."

echo "Cargo Tracker Docker image build and push"
read -r -p "Enter image tag (default latest): " IMAGE_TAG
IMAGE_TAG=$(echo "${IMAGE_TAG:-latest}" | tr '[:upper:]' '[:lower:]' | tr -cs 'a-z0-9._-' '-' | sed 's/^-*//;s/-*$//')
if [ -z "$IMAGE_TAG" ]; then
  IMAGE_TAG="latest"
fi

IMAGE_NAME=$(echo "$PROJECT_NAME" | tr '[:upper:]' '[:lower:]' | tr -cs 'a-z0-9' '-' | sed 's/^-*//;s/-*$//')
if [ -z "$IMAGE_NAME" ]; then
  echo "Unable to derive a valid image name" >&2
  exit 1
fi

echo "Select container registry:"
echo "1. Azure Container Registry (ACR)"
echo "2. Docker Hub"
read -r -p "Enter choice [1-2]: " REGISTRY_CHOICE

case "$REGISTRY_CHOICE" in
  1)
    read -r -p "Enter Azure Container Registry name (without .azurecr.io): " ACR_NAME
    if [ -z "$ACR_NAME" ]; then
      echo "ACR name is required" >&2
      exit 1
    fi
    echo "Logging in to ACR $ACR_NAME"
    az acr login --name "$ACR_NAME"
    REGISTRY_SERVER="${ACR_NAME}.azurecr.io"
    FULL_IMAGE_NAME="${REGISTRY_SERVER}/${IMAGE_NAME}:${IMAGE_TAG}"
    ;;
  2)
    read -r -p "Enter Docker Hub username: " DOCKER_USERNAME
    read -r -s -p "Enter Docker Hub password or access token: " DOCKER_PASSWORD
    echo
    if [ -z "$DOCKER_USERNAME" ] || [ -z "$DOCKER_PASSWORD" ]; then
      echo "Docker Hub username and password are required" >&2
      exit 1
    fi
    echo "$DOCKER_PASSWORD" | docker login --username "$DOCKER_USERNAME" --password-stdin
    FULL_IMAGE_NAME="${DOCKER_USERNAME}/${IMAGE_NAME}:${IMAGE_TAG}"
    ;;
  *)
    echo "Invalid registry choice" >&2
    exit 1
    ;;
esac

echo "Building Docker image $FULL_IMAGE_NAME"
docker build -f "$DOCKERFILE_PATH" -t "$FULL_IMAGE_NAME" "$BUILD_CONTEXT"

echo "Pushing Docker image $FULL_IMAGE_NAME"
docker push "$FULL_IMAGE_NAME"

echo "Image pushed successfully: $FULL_IMAGE_NAME"
