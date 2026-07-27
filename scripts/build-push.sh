#!/bin/bash
set -e

PROJECT_NAME="cargo-tracker"

echo "-------------------------------------------------------"
echo "Build and Push Docker Image for $PROJECT_NAME"
echo "-------------------------------------------------------"

# Prompt for Image Tag
read -p "Enter image tag [latest]: " IMAGE_TAG
IMAGE_TAG=${IMAGE_TAG:-latest}

# Sanitize Image Name
IMAGE_NAME=$(echo "$PROJECT_NAME" | tr '[:upper:]' '[:lower:]' | tr -cs 'a-z0-9' '-' | sed 's/^-*//;s/-*$//')

echo "Registry Selection:"
echo "1. AWS ECR"
echo "2. Docker Hub"
read -p "Select registry (1 or 2): " REGISTRY_CHOICE

if [ "$REGISTRY_CHOICE" == "1" ]; then
    read -p "Enter AWS Region [us-east-1]: " AWS_REGION
    AWS_REGION=${AWS_REGION:-us-east-1}
    read -p "Enter ECR Repository Name [$IMAGE_NAME]: " ECR_REPO
    ECR_REPO=${ECR_REPO:-$IMAGE_NAME}
    
    # Get AWS Account ID
    ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
    REGISTRY_URL="${ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"
    FULL_IMAGE_NAME="${REGISTRY_URL}/${ECR_REPO}:${IMAGE_TAG}"
    
    echo "Logging into AWS ECR..."
    aws ecr get-login-password --region $AWS_REGION | docker login --username AWS --password-stdin $REGISTRY_URL
    
    echo "Ensuring ECR repository exists..."
    aws ecr describe-repositories --repository-names $ECR_REPO --region $AWS_REGION >/dev/null 2>&1 || \
    aws ecr create-repository --repository-name $ECR_REPO --region $AWS_REGION
    
elif [ "$REGISTRY_CHOICE" == "2" ]; then
    read -p "Enter Docker Hub Username: " DOCKER_USERNAME
    REGISTRY_URL="docker.io/${DOCKER_USERNAME}"
    FULL_IMAGE_NAME="${REGISTRY_URL}/${IMAGE_NAME}:${IMAGE_TAG}"
    
    echo "Logging into Docker Hub..."
    docker login -u $DOCKER_USERNAME
else
    echo "Invalid choice. Exiting."
    exit 1
fi

echo "Building Docker image: $FULL_IMAGE_NAME..."
docker build -t "$FULL_IMAGE_NAME" .

echo "Pushing Docker image to registry..."
docker push "$FULL_IMAGE_NAME"

echo "-------------------------------------------------------"
echo "Successfully built and pushed: $FULL_IMAGE_NAME"
echo "-------------------------------------------------------"
