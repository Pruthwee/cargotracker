#!/bin/bash
set -e

# Build and push script for cargo-tracker on AWS ECR
# Usage: ./scripts/build-push.sh

PROJECT_NAME="cargotrackerMMono"
IMAGE_NAME=$(echo "$PROJECT_NAME" | tr '[:upper:]' '[:lower:]' | tr -cs 'a-z0-9' '-' | sed 's/^-*//;s/-*$//')

echo "=== Cargo Tracker Build and Push ==="
echo "Project name: $PROJECT_NAME"
echo "Image name: $IMAGE_NAME"
echo ""

# Ask for registry choice
echo "Choose registry:"
echo "1. AWS ECR"
echo "2. Docker Hub"
read -p "Enter choice (1 or 2): " REGISTRY_CHOICE

if [ "$REGISTRY_CHOICE" = "1" ]; then
  # AWS ECR
  read -p "Enter AWS region (e.g., us-east-1): " AWS_REGION
  read -p "Enter ECR registry URL (e.g., 123456789012.dkr.ecr.us-east-1.amazonaws.com): " REGISTRY_URL
  read -p "Enter ECR repository name (e.g., cargo-tracker): " ECR_REPO
  read -p "Enter image tag (or press Enter for latest): " IMAGE_TAG

  if [ -z "$IMAGE_TAG" ]; then
    IMAGE_TAG="latest"
  fi

  FULL_IMAGE_NAME="${REGISTRY_URL}/${ECR_REPO}:${IMAGE_TAG}"

  echo ""
  echo "=== Configuration ==="
  echo "Registry: AWS ECR"
  echo "Region: $AWS_REGION"
  echo "Registry URL: $REGISTRY_URL"
  echo "Repository: $ECR_REPO"
  echo "Image tag: $IMAGE_TAG"
  echo "Full image name: $FULL_IMAGE_NAME"
  echo ""

  echo "=== Logging in to AWS ECR ==="
  aws ecr get-login-password --region "$AWS_REGION" | docker login --username AWS --password-stdin "$REGISTRY_URL"

  echo ""
  echo "=== Creating ECR repository if it does not exist ==="
  aws ecr describe-repositories --repository-names "$ECR_REPO" --region "$AWS_REGION" >/dev/null 2>&1 || \
    aws ecr create-repository --repository-name "$ECR_REPO" --region "$AWS_REGION"

  echo ""
  echo "=== Building Docker image ==="
  docker build -f Dockerfile -t "$FULL_IMAGE_NAME" .

  echo ""
  echo "=== Pushing Docker image ==="
  docker push "$FULL_IMAGE_NAME"

  echo ""
  echo "=== Build and push complete ==="
  echo "Image: $FULL_IMAGE_NAME"

elif [ "$REGISTRY_CHOICE" = "2" ]; then
  # Docker Hub
  read -p "Enter Docker Hub username: " DOCKER_USERNAME
  read -s -p "Enter Docker Hub password: " DOCKER_PASSWORD
  echo ""
  read -p "Enter image tag (or press Enter for latest): " IMAGE_TAG

  if [ -z "$IMAGE_TAG" ]; then
    IMAGE_TAG="latest"
  fi

  FULL_IMAGE_NAME="${DOCKER_USERNAME}/${IMAGE_NAME}:${IMAGE_TAG}"

  echo ""
  echo "=== Configuration ==="
  echo "Registry: Docker Hub"
  echo "Username: $DOCKER_USERNAME"
  echo "Image tag: $IMAGE_TAG"
  echo "Full image name: $FULL_IMAGE_NAME"
  echo ""

  echo "=== Logging in to Docker Hub ==="
  echo "$DOCKER_PASSWORD" | docker login --username "$DOCKER_USERNAME" --password-stdin

  echo ""
  echo "=== Building Docker image ==="
  docker build -f Dockerfile -t "$FULL_IMAGE_NAME" .

  echo ""
  echo "=== Pushing Docker image ==="
  docker push "$FULL_IMAGE_NAME"

  echo ""
  echo "=== Build and push complete ==="
  echo "Image: $FULL_IMAGE_NAME"

else
  echo "Invalid choice. Exiting."
  exit 1
fi
