#!/bin/bash
set -e

# Build and push script for cargo-tracker to AWS ECR
# Usage: ./scripts/build-push.sh

PROJECT_NAME="cargo-tracker"

echo "=== Cargo Tracker Build and Push ==="

# Ask for target platform
echo ""
echo "Select target platform:"
echo "  1. AWS ECR"
echo "  2. Docker Hub"
read -p "Enter choice (1 or 2): " PLATFORM_CHOICE

if [ "$PLATFORM_CHOICE" = "1" ]; then
    # AWS ECR
    read -p "Enter AWS region (e.g., us-east-1): " AWS_REGION
    read -p "Enter ECR registry URL (e.g., 123456789.dkr.ecr.us-east-1.amazonaws.com): " REGISTRY_URL
    read -p "Enter ECR repository name: " ECR_REPO
    read -p "Enter image tag (or press Enter for 'latest'): " IMAGE_TAG

    if [ -z "$IMAGE_TAG" ]; then
        IMAGE_TAG="latest"
    fi

    # Sanitize image name
    IMAGE_NAME=$(echo "$PROJECT_NAME" | tr '[:upper:]' '[:lower:]' | tr -cs 'a-z0-9' '-' | sed 's/^-*//;s/-*$//')

    FULL_IMAGE_NAME="${REGISTRY_URL}/${ECR_REPO}:${IMAGE_TAG}"

    echo ""
    echo "=== Logging in to AWS ECR ==="
    aws ecr get-login-password --region "$AWS_REGION" | docker login --username AWS --password-stdin "$REGISTRY_URL"
    if [ $? -ne 0 ]; then
        echo "ERROR: ECR login failed"
        exit 1
    fi

    # Create repository if it doesn't exist
    echo ""
    echo "=== Checking ECR repository ==="
    aws ecr describe-repositories --repository-names "$ECR_REPO" --region "$AWS_REGION" >/dev/null 2>&1
    if [ $? -ne 0 ]; then
        echo "Creating ECR repository: $ECR_REPO"
        aws ecr create-repository --repository-name "$ECR_REPO" --region "$AWS_REGION"
    else
        echo "ECR repository already exists: $ECR_REPO"
    fi

    echo ""
    echo "=== Building Docker image ==="
    echo "Image: $FULL_IMAGE_NAME"
    docker build -f Dockerfile -t "$FULL_IMAGE_NAME" .
    if [ $? -ne 0 ]; then
        echo "ERROR: Docker build failed"
        exit 1
    fi

    echo ""
    echo "=== Pushing Docker image ==="
    docker push "$FULL_IMAGE_NAME"
    if [ $? -ne 0 ]; then
        echo "ERROR: Docker push failed"
        exit 1
    fi

    echo ""
    echo "=== Success ==="
    echo "Image pushed to: $FULL_IMAGE_NAME"

elif [ "$PLATFORM_CHOICE" = "2" ]; then
    # Docker Hub
    read -p "Enter Docker Hub username: " DOCKER_USERNAME
    read -s -p "Enter Docker Hub password: " DOCKER_PASSWORD
    echo ""
    read -p "Enter image tag (or press Enter for 'latest'): " IMAGE_TAG

    if [ -z "$IMAGE_TAG" ]; then
        IMAGE_TAG="latest"
    fi

    # Sanitize image name
    IMAGE_NAME=$(echo "$PROJECT_NAME" | tr '[:upper:]' '[:lower:]' | tr -cs 'a-z0-9' '-' | sed 's/^-*//;s/-*$//')

    FULL_IMAGE_NAME="${DOCKER_USERNAME}/${IMAGE_NAME}:${IMAGE_TAG}"

    echo ""
    echo "=== Logging in to Docker Hub ==="
    echo "$DOCKER_PASSWORD" | docker login --username "$DOCKER_USERNAME" --password-stdin
    if [ $? -ne 0 ]; then
        echo "ERROR: Docker Hub login failed"
        exit 1
    fi

    echo ""
    echo "=== Building Docker image ==="
    echo "Image: $FULL_IMAGE_NAME"
    docker build -f Dockerfile -t "$FULL_IMAGE_NAME" .
    if [ $? -ne 0 ]; then
        echo "ERROR: Docker build failed"
        exit 1
    fi

    echo ""
    echo "=== Pushing Docker image ==="
    docker push "$FULL_IMAGE_NAME"
    if [ $? -ne 0 ]; then
        echo "ERROR: Docker push failed"
        exit 1
    fi

    echo ""
    echo "=== Success ==="
    echo "Image pushed to: $FULL_IMAGE_NAME"

else
    echo "ERROR: Invalid choice. Please select 1 or 2."
    exit 1
fi
