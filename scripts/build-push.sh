#!/bin/bash
set -e

echo "-------------------------------------------------------"
echo "Cargo Tracker - Build and Push Script"
echo "-------------------------------------------------------"

# Project Name
PROJECT_NAME="cargo-tracker"

# Prompt for Image Tag
read -p "Enter image tag [latest]: " IMAGE_TAG
IMAGE_TAG=${IMAGE_TAG:-latest}

# Sanitize Image Tag (trim leading/trailing hyphens)
IMAGE_TAG=$(echo "$IMAGE_TAG" | sed 's/^-*//;s/-*$//')

# Registry Selection
echo "Select Registry:"
echo "1) AWS ECR"
echo "2) Docker Hub"
read -p "Choice [1-2]: " REGISTRY_CHOICE

if [ "$REGISTRY_CHOICE" == "1" ]; then
    read -p "Enter AWS Region [us-east-1]: " AWS_REGION
    AWS_REGION=${AWS_REGION:-us-east-1}
    read -p "Enter ECR Repository Name [$PROJECT_NAME]: " ECR_REPO
    ECR_REPO=${ECR_REPO:-$PROJECT_NAME}
    
    # ECR Login
    echo "Logging into AWS ECR..."
    aws ecr get-login-password --region $AWS_REGION | docker login --username AWS --password-stdin aws_account_id.dkr.ecr.$AWS_REGION.amazonaws.com
    
    # ECR Repository Auto-Creation
    echo "Checking if ECR repository $ECR_REPO exists..."
    aws ecr describe-repositories --repository-names $ECR_REPO --region $AWS_REGION >/dev/null 2>&1 || {
        echo "Creating ECR repository $ECR_REPO..."
        aws ecr create-repository --repository-name $ECR_REPO --region $AWS_REGION
    }
    
    REGISTRY_URL="aws_account_id.dkr.ecr.$AWS_REGION.amazonaws.com"
    FULL_IMAGE_NAME="$REGISTRY_URL/$ECR_REPO:$IMAGE_TAG"
else
    read -p "Enter Docker Hub Username: " DOCKER_USERNAME
    read -sp "Enter Docker Hub Password: " DOCKER_PASSWORD
    echo ""
    
    echo "Logging into Docker Hub..."
    echo "$DOCKER_PASSWORD" | docker login --username "$DOCKER_USERNAME" --password-stdin
    
    FULL_IMAGE_NAME="$DOCKER_USERNAME/$PROJECT_NAME:$IMAGE_TAG"
fi

# Sanitize Project Name for Image
IMAGE_NAME=$(echo "$PROJECT_NAME" | tr '[:upper:]' '[:lower:]' | tr -cs 'a-z0-9' '-' | sed 's/^-*//;s/-*$//')

echo "Building Docker image: $FULL_IMAGE_NAME..."
docker build -t "$FULL_IMAGE_NAME" .

echo "Pushing Docker image to registry..."
docker push "$FULL_IMAGE_NAME"

echo "-------------------------------------------------------"
echo "Successfully built and pushed: $FULL_IMAGE_NAME"
echo "-------------------------------------------------------"
