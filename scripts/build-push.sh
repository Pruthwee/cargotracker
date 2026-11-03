#!/bin/bash
set -e
set -o pipefail

echo "=== Cargo Tracker Build and Push Script ==="
echo ""

# Project name
PROJECT_NAME="cargo-tracker"
IMAGE_NAME=$(echo "$PROJECT_NAME" | tr '[:upper:]' '[:lower:]' | tr -cs 'a-z0-9' '-' | sed 's/^-*//;s/-*$//')

# Ask for registry details
echo "Select target registry:"
echo "1) AWS ECR"
echo "2) Docker Hub"
read -p "Enter choice (1 or 2): " REGISTRY_CHOICE

if [ "$REGISTRY_CHOICE" = "1" ]; then
  echo ""
  echo "=== AWS ECR ==="
  read -p "Enter AWS region (e.g., us-east-1): " AWS_REGION
  read -p "Enter ECR repository name: " ECR_REPO
  REGISTRY_URL="${AWS_REGION}.dkr.ecr.${AWS_REGION}.amazonaws.com"
  FULL_REGISTRY="${REGISTRY_URL}"
elif [ "$REGISTRY_CHOICE" = "2" ]; then
  echo ""
  echo "=== Docker Hub ==="
  read -p "Enter Docker Hub username: " DOCKER_USERNAME
  FULL_REGISTRY="${DOCKER_USERNAME}"
else
  echo "Invalid choice. Exiting."
  exit 1
fi

# Ask for image tag
read -p "Enter image tag (press Enter for 'latest'): " IMAGE_TAG
if [ -z "$IMAGE_TAG" ]; then
  IMAGE_TAG="latest"
fi

# Sanitize tag
IMAGE_TAG=$(echo "$IMAGE_TAG" | tr -cs 'a-z0-9-.' '-' | sed 's/^-*//;s/-*$//')

FULL_IMAGE_NAME="${FULL_REGISTRY}/${IMAGE_NAME}:${IMAGE_TAG}"

echo ""
echo "=== Build Configuration ==="
echo "Project:        $PROJECT_NAME"
echo "Image name:     $IMAGE_NAME"
echo "Full image:     $FULL_IMAGE_NAME"
echo "Build context:  ."
echo ""

# Build the Docker image
echo "=== Building Docker image ==="
docker build -f Dockerfile -t "$FULL_IMAGE_NAME" .
echo "Docker build completed successfully."
echo ""

# Login and push
if [ "$REGISTRY_CHOICE" = "1" ]; then
  echo "=== Logging in to AWS ECR ==="
  aws ecr get-login-password --region "$AWS_REGION" | docker login --username AWS --password-stdin "$REGISTRY_URL"
  echo "ECR login successful."

  echo "=== Creating ECR repository if it does not exist ==="
  aws ecr describe-repositories --repository-names "$ECR_REPO" --region "$AWS_REGION" >/dev/null 2>&1 || \
    aws ecr create-repository --repository-name "$ECR_REPO" --region "$AWS_REGION"
  echo "ECR repository ready."
elif [ "$REGISTRY_CHOICE" = "2" ]; then
  echo "=== Logging in to Docker Hub ==="
  read -s -p "Enter Docker Hub password: " DOCKER_PASSWORD
  echo ""
  echo "$DOCKER_PASSWORD" | docker login --username "$DOCKER_USERNAME" --password-stdin
  if [ $? -ne 0 ]; then
    echo "Docker Hub login failed."
    exit 1
  fi
  echo "Docker Hub login successful."
fi

echo ""
echo "=== Pushing Docker image ==="
docker push "$FULL_IMAGE_NAME"
echo "Docker push completed successfully."
echo ""
echo "Image pushed: $FULL_IMAGE_NAME"
