#!/bin/bash
set -e
set -o pipefail

echo "=== Cargo Tracker - Build and Push to AWS ECR ==="

# Project name
PROJECT_NAME="cargo-tracker"
IMAGE_NAME=$(echo "$PROJECT_NAME" | tr '[:upper:]' '[:lower:]' | tr -cs 'a-z0-9' '-' | sed 's/^-*//;s/-*$//')

# Ask for AWS region
echo ""
echo "Target platform: AWS ECR"
read -p "Enter AWS region (e.g., us-east-1): " AWS_REGION
if [ -z "$AWS_REGION" ]; then
  echo "AWS region is required."
  exit 1
fi

# Ask for ECR repository name
read -p "Enter ECR repository name (default: $IMAGE_NAME): " ECR_REPO
ECR_REPO=${ECR_REPO:-$IMAGE_NAME}

# Ask for image tag
read -p "Enter image tag (default: latest): " IMAGE_TAG
IMAGE_TAG=${IMAGE_TAG:-latest}

# AWS account ID
read -p "Enter AWS account ID: " AWS_ACCOUNT_ID
if [ -z "$AWS_ACCOUNT_ID" ]; then
  echo "AWS account ID is required."
  exit 1
fi

REGISTRY_URL="${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"
FULL_IMAGE_NAME="${REGISTRY_URL}/${ECR_REPO}:${IMAGE_TAG}"

echo ""
echo "Building image: $FULL_IMAGE_NAME"
echo "Platform: linux/amd64"
echo ""

# Build the Docker image
echo "=== Building Docker image ==="
docker build -f Dockerfile -t "$FULL_IMAGE_NAME" --platform linux/amd64 .

# Login to AWS ECR
echo ""
echo "=== Logging in to AWS ECR ==="
aws ecr get-login-password --region "$AWS_REGION" | docker login --username AWS --password-stdin "$REGISTRY_URL"

# Create ECR repository if it doesn't exist
echo ""
echo "=== Checking ECR repository ==="
aws ecr describe-repositories --repository-names "$ECR_REPO" --region "$AWS_REGION" >/dev/null 2>&1 || \
  aws ecr create-repository --repository-name "$ECR_REPO" --region "$AWS_REGION"

# Push the image
echo ""
echo "=== Pushing Docker image ==="
docker push "$FULL_IMAGE_NAME"

echo ""
echo "=== Build and push complete ==="
echo "Image: $FULL_IMAGE_NAME"
echo "Registry: $REGISTRY_URL"
echo "Repository: $ECR_REPO"
echo "Tag: $IMAGE_TAG"
