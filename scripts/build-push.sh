#!/bin/bash
set -e
set -o pipefail

# Build and push script for Cargo Tracker on AWS ECR

echo "=== Cargo Tracker Build and Push ==="

PROJECT_NAME="Cargo-MONO-1"
IMAGE_NAME=$(echo "$PROJECT_NAME" | tr '[:upper:]' '[:lower:]' | tr -cs 'a-z0-9' '-' | sed 's/^-*//;s/-*$//')

# Ask for AWS region
read -p "Enter AWS region (e.g., us-east-1): " AWS_REGION
if [ -z "$AWS_REGION" ]; then
  echo "AWS region is required."
  exit 1
fi

# Ask for ECR repository name (defaults to image name)
read -p "Enter ECR repository name (default: $IMAGE_NAME): " ECR_REPO
ECR_REPO=${ECR_REPO:-$IMAGE_NAME}

# Ask for image tag (defaults to latest)
read -p "Enter image tag (default: latest): " IMAGE_TAG
IMAGE_TAG=${IMAGE_TAG:-latest}

# Get AWS account ID
AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query 'Account' --output text --region "$AWS_REGION")
if [ -z "$AWS_ACCOUNT_ID" ]; then
  echo "Could not retrieve AWS account ID."
  exit 1
fi

REGISTRY_URL="$AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com"
FULL_IMAGE_NAME="$REGISTRY_URL/$ECR_REPO:$IMAGE_TAG"

echo ""
echo "Project: $PROJECT_NAME"
echo "Image: $FULL_IMAGE_NAME"
echo "Registry: $REGISTRY_URL"
echo ""

# Login to ECR
echo "Logging in to Amazon ECR..."
aws ecr get-login-password --region "$AWS_REGION" | docker login --username AWS --password-stdin "$REGISTRY_URL"

# Create ECR repository if it doesn't exist
echo "Checking if ECR repository exists..."
aws ecr describe-repositories --repository-names "$ECR_REPO" --region "$AWS_REGION" >/dev/null 2>&1 || {
  echo "Creating ECR repository: $ECR_REPO"
  aws ecr create-repository --repository-name "$ECR_REPO" --region "$AWS_REGION"
}

# Build the Docker image
echo "Building Docker image..."
docker build -f Dockerfile -t "$FULL_IMAGE_NAME" .

# Push the Docker image
echo "Pushing Docker image..."
docker push "$FULL_IMAGE_NAME"

echo ""
echo "=== Build and push complete ==="
echo "Image: $FULL_IMAGE_NAME"
