#!/bin/bash
set -e

PROJECT_NAME="cargo-tracker"

echo "Select Registry Type:"
echo "1. AWS ECR"
echo "2. Docker Hub"
read -p "Choice [1-2]: " REGISTRY_CHOICE

if [ "$REGISTRY_CHOICE" == "1" ]; then
    read -p "Enter AWS Region (e.g., us-east-1): " AWS_REGION
    read -p "Enter ECR Repository Name: " ECR_REPO
    
    # Login to ECR
    aws ecr get-login-password --region $AWS_REGION | docker login --username AWS --password-stdin aws_account_id.dkr.ecr.$AWS_REGION.amazonaws.com
    
    # Ensure repository exists
    aws ecr describe-repositories --repository-names $ECR_REPO --region $AWS_REGION >/dev/null 2>&1 || aws ecr create-repository --repository-name $ECR_REPO --region $AWS_REGION
    
    REGISTRY_URL="aws_account_id.dkr.ecr.$AWS_REGION.amazonaws.com"
    REPO_NAME="$ECR_REPO"
else
    read -p "Enter Docker Hub Username: " DOCKER_USERNAME
    read -s -p "Enter Docker Hub Password: " DOCKER_PASSWORD
    echo ""
    echo "$DOCKER_PASSWORD" | docker login --username "$DOCKER_USERNAME" --password-stdin
    
    REGISTRY_URL="$DOCKER_USERNAME"
    REPO_NAME="$PROJECT_NAME"
fi

read -p "Enter Image Tag (default: latest): " IMAGE_TAG
IMAGE_TAG=${IMAGE_TAG:-latest}

# Sanitize image name
IMAGE_NAME=$(echo "$PROJECT_NAME" | tr '[:upper:]' '[:lower:]' | tr -cs 'a-z0-9' '-' | sed 's/^-*//;s/-*$//')
FULL_IMAGE_NAME="$REGISTRY_URL/$IMAGE_NAME:$IMAGE_TAG"

echo "Building image: $FULL_IMAGE_NAME..."
docker build -t "$FULL_IMAGE_NAME" .

echo "Pushing image..."
docker push "$FULL_IMAGE_NAME"

echo "Successfully built and pushed $FULL_IMAGE_NAME"
