#!/bin/bash
set -e
set -o pipefail

PROJECT_NAME="cargo-tracker"
IMAGE_NAME=$(echo "$PROJECT_NAME" | tr '[:upper:]' '[:lower:]' | tr -cs 'a-z0-9' '-' | sed 's/^-*//;s/-*$//')

echo "=== Cargo Tracker Build and Push ==="
echo "Project name: $PROJECT_NAME"
echo "Image name: $IMAGE_NAME"

echo ""
echo "Select target platform:"
echo "1. AWS ECR"
echo "2. Docker Hub"
read -p "Enter choice (1 or 2): " PLATFORM_CHOICE

if [ "$PLATFORM_CHOICE" = "1" ]; then
  echo ""
  echo "=== AWS ECR Configuration ==="
  read -p "Enter AWS region: " AWS_REGION
  read -p "Enter AWS account ID: " AWS_ACCOUNT_ID
  read -p "Enter ECR repository name (press Enter for default '$IMAGE_NAME'): " ECR_REPO
  ECR_REPO=${ECR_REPO:-$IMAGE_NAME}
  read -p "Enter image tag (press Enter for 'latest'): " IMAGE_TAG
  IMAGE_TAG=${IMAGE_TAG:-latest}

  REGISTRY_URL="${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"
  FULL_IMAGE_NAME="${REGISTRY_URL}/${ECR_REPO}:${IMAGE_TAG}"

  echo ""
  echo "=== Logging in to AWS ECR ==="
  aws ecr get-login-password --region "$AWS_REGION" | docker login --username AWS --password-stdin "$REGISTRY_URL"
  echo "ECR login successful."

  echo ""
  echo "=== Creating ECR repository if it does not exist ==="
  aws ecr describe-repositories --repository-names "$ECR_REPO" --region "$AWS_REGION" >/dev/null 2>&1 || \
    aws ecr create-repository --repository-name "$ECR_REPO" --region "$AWS_REGION"
  echo "ECR repository ready: $ECR_REPO"

  echo ""
  echo "=== Building Docker image ==="
  docker build -f Dockerfile -t "$FULL_IMAGE_NAME" .
  echo "Docker image built: $FULL_IMAGE_NAME"

  echo ""
  echo "=== Pushing Docker image to ECR ==="
  docker push "$FULL_IMAGE_NAME"
  echo "Docker image pushed to ECR: $FULL_IMAGE_NAME"

  echo ""
  echo "=== Build and push complete ==="
  echo "Image URI: $FULL_IMAGE_NAME"

elif [ "$PLATFORM_CHOICE" = "2" ]; then
  echo ""
  echo "=== Docker Hub Configuration ==="
  read -p "Enter Docker Hub username: " DOCKER_USERNAME
  read -s -p "Enter Docker Hub password: " DOCKER_PASSWORD
  echo ""
  read -p "Enter image tag (press Enter for 'latest'): " IMAGE_TAG
  IMAGE_TAG=${IMAGE_TAG:-latest}

  FULL_IMAGE_NAME="${DOCKER_USERNAME}/${IMAGE_NAME}:${IMAGE_TAG}"

  echo ""
  echo "=== Logging in to Docker Hub ==="
  echo "$DOCKER_PASSWORD" | docker login --username "$DOCKER_USERNAME" --password-stdin
  echo "Docker Hub login successful."

  echo ""
  echo "=== Building Docker image ==="
  docker build -f Dockerfile -t "$FULL_IMAGE_NAME" .
  echo "Docker image built: $FULL_IMAGE_NAME"

  echo ""
  echo "=== Pushing Docker image to Docker Hub ==="
  docker push "$FULL_IMAGE_NAME"
  echo "Docker image pushed to Docker Hub: $FULL_IMAGE_NAME"

  echo ""
  echo "=== Build and push complete ==="
  echo "Image URI: $FULL_IMAGE_NAME"

else
  echo "Invalid choice. Exiting."
  exit 1
fi
