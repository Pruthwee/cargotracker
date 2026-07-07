#!/bin/bash
set -e
set -o pipefail

# ============================================================
# build-push.sh - Build and push Docker image for cargo-tracker
# ============================================================

PROJECT_NAME="cargo-tracker"
DOCKERFILE_PATH="Dockerfile"

echo "=============================================="
echo "  cargo-tracker - Docker Build & Push Script"
echo "=============================================="
echo ""

# ---- Registry selection ----
echo "Select container registry:"
echo "  1. AWS ECR (Elastic Container Registry)"
echo "  2. Docker Hub"
echo ""
read -rp "Enter choice [1 or 2]: " REGISTRY_CHOICE

# ---- Tag input ----
read -rp "Enter image tag (press Enter for 'latest'): " RAW_TAG

# Sanitize image name
IMAGE_NAME=$(echo "$PROJECT_NAME" | tr '[:upper:]' '[:lower:]' | tr -cs 'a-z0-9' '-' | sed 's/^-*//;s/-*$//')

# Sanitize tag
if [ -z "$RAW_TAG" ]; then
  IMAGE_TAG="latest"
else
  IMAGE_TAG=$(echo "$RAW_TAG" | tr '[:upper:]' '[:lower:]' | tr -cs 'a-z0-9._-' '-' | sed 's/^-*//;s/-*$//')
  if [ -z "$IMAGE_TAG" ]; then
    IMAGE_TAG="latest"
  fi
fi

echo ""
echo "Image name : $IMAGE_NAME"
echo "Image tag  : $IMAGE_TAG"
echo ""

# ============================================================
# AWS ECR
# ============================================================
if [ "$REGISTRY_CHOICE" = "1" ]; then
  read -rp "Enter AWS Region (e.g. us-east-1): " AWS_REGION
  read -rp "Enter AWS Account ID: " AWS_ACCOUNT_ID
  read -rp "Enter ECR repository name [${IMAGE_NAME}]: " ECR_REPO_INPUT
  ECR_REPO="${ECR_REPO_INPUT:-$IMAGE_NAME}"

  REGISTRY_URL="${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"
  FULL_IMAGE_NAME="${REGISTRY_URL}/${ECR_REPO}:${IMAGE_TAG}"

  echo ""
  echo "Authenticating with AWS ECR..."
  aws ecr get-login-password --region "$AWS_REGION" | \
    docker login --username AWS --password-stdin "$REGISTRY_URL"

  echo "Checking/creating ECR repository: $ECR_REPO ..."
  aws ecr describe-repositories --repository-names "$ECR_REPO" --region "$AWS_REGION" >/dev/null 2>&1 || \
    aws ecr create-repository --repository-name "$ECR_REPO" --region "$AWS_REGION"

# ============================================================
# Docker Hub
# ============================================================
elif [ "$REGISTRY_CHOICE" = "2" ]; then
  read -rp "Enter Docker Hub username: " DOCKER_USERNAME
  read -rsp "Enter Docker Hub password/token: " DOCKER_PASSWORD
  echo ""
  read -rp "Enter Docker Hub namespace/org [${DOCKER_USERNAME}]: " DOCKER_NAMESPACE_INPUT
  DOCKER_NAMESPACE="${DOCKER_NAMESPACE_INPUT:-$DOCKER_USERNAME}"

  REGISTRY_URL="docker.io"
  FULL_IMAGE_NAME="${DOCKER_NAMESPACE}/${IMAGE_NAME}:${IMAGE_TAG}"

  echo ""
  echo "Authenticating with Docker Hub..."
  echo "$DOCKER_PASSWORD" | docker login --username "$DOCKER_USERNAME" --password-stdin

else
  echo "ERROR: Invalid choice. Please enter 1 or 2."
  exit 1
fi

echo ""
echo "Full image: $FULL_IMAGE_NAME"
echo ""

# ---- Build ----
echo "Building Docker image..."
docker build -f "$DOCKERFILE_PATH" -t "$FULL_IMAGE_NAME" .
echo "Build successful."

# ---- Tag as latest (if not already) ----
if [ "$IMAGE_TAG" != "latest" ]; then
  LATEST_IMAGE="${FULL_IMAGE_NAME%:*}:latest"
  docker tag "$FULL_IMAGE_NAME" "$LATEST_IMAGE"
  echo "Tagged as: $LATEST_IMAGE"
fi

# ---- Push ----
echo ""
echo "Pushing image: $FULL_IMAGE_NAME ..."
docker push "$FULL_IMAGE_NAME"

if [ "$IMAGE_TAG" != "latest" ]; then
  echo "Pushing image: $LATEST_IMAGE ..."
  docker push "$LATEST_IMAGE"
fi

echo ""
echo "=============================================="
echo "  Image pushed successfully!"
echo "  $FULL_IMAGE_NAME"
echo "=============================================="
