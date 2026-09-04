#!/bin/bash
# =============================================================================
# deploy-image.sh - Deploy Eclipse Cargo Tracker to AWS ECS Fargate
# =============================================================================

set -e
set -o pipefail

SERVICE_NAME="cargo-tracker-service"
TASK_FAMILY="cargo-tracker-task"
LOG_GROUP="/ecs/cargo-tracker"
CONTAINER_NAME="cargo-tracker"
CONTAINER_PORT=8080

echo "=============================================="
echo "  Eclipse Cargo Tracker - ECS Fargate Deploy"
echo "=============================================="
echo ""

# -------------------------------------------------------------------------
# Collect configuration
# -------------------------------------------------------------------------
read -rp "Enter AWS Region [us-east-1]: " AWS_REGION
AWS_REGION="${AWS_REGION:-us-east-1}"

read -rp "Enter ECS Cluster name [cargo-tracker-cluster]: " CLUSTER_NAME
CLUSTER_NAME="${CLUSTER_NAME:-cargo-tracker-cluster}"

read -rp "Enter VPC ID (e.g., vpc-xxxxxxxx): " VPC_ID

read -rp "Enter Subnet IDs (comma-separated, e.g., subnet-aaa,subnet-bbb): " SUBNETS_INPUT
SUBNET_1=$(echo "$SUBNETS_INPUT" | cut -d',' -f1 | tr -d ' ')
SUBNET_2=$(echo "$SUBNETS_INPUT" | cut -d',' -f2 | tr -d ' ')
if [ -z "$SUBNET_2" ]; then
  SUBNET_2="$SUBNET_1"
fi

read -rp "Enter Security Group ID (e.g., sg-xxxxxxxx): " SECURITY_GROUP

read -rp "Enter ECR Image URI (e.g., 123456789.dkr.ecr.us-east-1.amazonaws.com/cargo-tracker:latest): " IMAGE_URI

# -------------------------------------------------------------------------
# Get AWS Account ID
# -------------------------------------------------------------------------
echo ""
echo "Retrieving AWS Account ID..."
ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
if [ -z "$ACCOUNT_ID" ]; then
  echo "ERROR: Could not retrieve AWS Account ID. Ensure AWS CLI is configured."
  exit 1
fi
echo "AWS Account ID: $ACCOUNT_ID"

# -------------------------------------------------------------------------
# Ensure CloudWatch log group exists
# -------------------------------------------------------------------------
echo ""
echo "Ensuring CloudWatch log group '$LOG_GROUP' exists..."
aws logs create-log-group --log-group-name "$LOG_GROUP" --region "$AWS_REGION" 2>/dev/null || true
echo "Log group ready: $LOG_GROUP"

# -------------------------------------------------------------------------
# Check / create ECS cluster
# -------------------------------------------------------------------------
echo ""
echo "Checking ECS cluster '$CLUSTER_NAME'..."
CLUSTER_STATUS=$(aws ecs describe-clusters --clusters "$CLUSTER_NAME" --region "$AWS_REGION" \
  --query "clusters[0].status" --output text 2>/dev/null || echo "MISSING")

if [ "$CLUSTER_STATUS" != "ACTIVE" ]; then
  echo "Cluster not found or inactive. Creating cluster '$CLUSTER_NAME'..."
  aws ecs create-cluster --cluster-name "$CLUSTER_NAME" --region "$AWS_REGION"
  echo "Cluster created."
else
  echo "Cluster '$CLUSTER_NAME' is ACTIVE."
fi

# -------------------------------------------------------------------------
# Load balancer prompt
# -------------------------------------------------------------------------
echo ""
read -rp "Do you need an Application Load Balancer for this service? (y/n) [y]: " NEED_LB
NEED_LB="${NEED_LB:-y}"

TARGET_GROUP_ARN=""
ALB_DNS=""

if [[ "$NEED_LB" =~ ^[Yy]$ ]]; then
  echo ""
  echo "Creating Application Load Balancer and Target Group..."

  # Create ALB
  ALB_NAME="cargo-tracker-alb"
  echo "Creating ALB '$ALB_NAME'..."
  ALB_ARN=$(aws elbv2 create-load-balancer \
    --name "$ALB_NAME" \
    --subnets "$SUBNET_1" "$SUBNET_2" \
    --security-groups "$SECURITY_GROUP" \
    --scheme internet-facing \
    --type application \
    --ip-address-type ipv4 \
    --region "$AWS_REGION" \
    --query "LoadBalancers[0].LoadBalancerArn" \
    --output text)
  echo "ALB ARN: $ALB_ARN"

  ALB_DNS=$(aws elbv2 describe-load-balancers \
    --load-balancer-arns "$ALB_ARN" \
    --region "$AWS_REGION" \
    --query "LoadBalancers[0].DNSName" \
    --output text)

  # Create Target Group (target-type ip required for Fargate awsvpc mode)
  TG_NAME="cargo-tracker-tg"
  echo "Creating Target Group '$TG_NAME' (target-type: ip)..."
  TARGET_GROUP_ARN=$(aws elbv2 create-target-group \
    --name "$TG_NAME" \
    --protocol HTTP \
    --port "$CONTAINER_PORT" \
    --vpc-id "$VPC_ID" \
    --target-type ip \
    --health-check-protocol HTTP \
    --health-check-path "/cargo-tracker/rest/health" \
    --health-check-interval-seconds 30 \
    --health-check-timeout-seconds 10 \
    --healthy-threshold-count 2 \
    --unhealthy-threshold-count 5 \
    --region "$AWS_REGION" \
    --query "TargetGroups[0].TargetGroupArn" \
    --output text)
  echo "Target Group ARN: $TARGET_GROUP_ARN"

  # Create ALB Listener
  echo "Creating ALB Listener on port 80..."
  aws elbv2 create-listener \
    --load-balancer-arn "$ALB_ARN" \
    --protocol HTTP \
    --port 80 \
    --default-actions "Type=forward,TargetGroupArn=$TARGET_GROUP_ARN" \
    --region "$AWS_REGION" >/dev/null
  echo "ALB Listener created."
fi

# -------------------------------------------------------------------------
# Prepare task definition JSON (replace placeholders)
# -------------------------------------------------------------------------
echo ""
echo "Preparing task definition..."
TASK_DEF_FILE="/tmp/cargo-tracker-task-def-$$.json"
cp "$(dirname "$0")/../ecs/task-definition.json" "$TASK_DEF_FILE"

sed -i "s|{{ACCOUNT_ID}}|${ACCOUNT_ID}|g" "$TASK_DEF_FILE"
sed -i "s|{{AWS_REGION}}|${AWS_REGION}|g" "$TASK_DEF_FILE"
sed -i "s|{{IMAGE_URI}}|${IMAGE_URI}|g" "$TASK_DEF_FILE"
sed -i "s|{{MEMCACHED_ENDPOINT}}||g" "$TASK_DEF_FILE"
sed -i "s|{{DB_JDBC_URL}}|jdbc:h2:file:/app/cargo-tracker-data/cargo-tracker-database|g" "$TASK_DEF_FILE"
sed -i "s|{{DB_USER}}||g" "$TASK_DEF_FILE"

# -------------------------------------------------------------------------
# Register task definition
# -------------------------------------------------------------------------
echo "Registering ECS task definition..."
TASK_DEF_ARN=$(aws ecs register-task-definition \
  --cli-input-json "file://${TASK_DEF_FILE}" \
  --region "$AWS_REGION" \
  --query "taskDefinition.taskDefinitionArn" \
  --output text)
echo "Task Definition ARN: $TASK_DEF_ARN"
rm -f "$TASK_DEF_FILE"

# -------------------------------------------------------------------------
# Prepare service definition JSON (replace placeholders)
# -------------------------------------------------------------------------
echo ""
echo "Preparing service definition..."
SVC_DEF_FILE="/tmp/cargo-tracker-svc-def-$$.json"
cp "$(dirname "$0")/../ecs/service-definition.json" "$SVC_DEF_FILE"

sed -i "s|{{CLUSTER_NAME}}|${CLUSTER_NAME}|g" "$SVC_DEF_FILE"
sed -i "s|{{SUBNET_1}}|${SUBNET_1}|g" "$SVC_DEF_FILE"
sed -i "s|{{SUBNET_2}}|${SUBNET_2}|g" "$SVC_DEF_FILE"
sed -i "s|{{SECURITY_GROUP}}|${SECURITY_GROUP}|g" "$SVC_DEF_FILE"

# Add load balancer config if needed
if [[ "$NEED_LB" =~ ^[Yy]$ ]] && [ -n "$TARGET_GROUP_ARN" ]; then
  # Inject loadBalancers and healthCheckGracePeriodSeconds into service definition
  python3 -c "
import json, sys
with open('$SVC_DEF_FILE') as f:
    svc = json.load(f)
svc['loadBalancers'] = [{
    'targetGroupArn': '$TARGET_GROUP_ARN',
    'containerName': '$CONTAINER_NAME',
    'containerPort': $CONTAINER_PORT
}]
svc['healthCheckGracePeriodSeconds'] = 300
with open('$SVC_DEF_FILE', 'w') as f:
    json.dump(svc, f, indent=2)
" 2>/dev/null || {
    # Fallback: use sed to inject loadBalancers before closing brace
    sed -i "s|}$|,\"loadBalancers\":[{\"targetGroupArn\":\"${TARGET_GROUP_ARN}\",\"containerName\":\"${CONTAINER_NAME}\",\"containerPort\":${CONTAINER_PORT}}],\"healthCheckGracePeriodSeconds\":300}|" "$SVC_DEF_FILE"
  }
fi

# -------------------------------------------------------------------------
# Create or update ECS service
# -------------------------------------------------------------------------
echo ""
echo "Checking if ECS service '$SERVICE_NAME' exists..."
EXISTING_SERVICE=$(aws ecs describe-services \
  --cluster "$CLUSTER_NAME" \
  --services "$SERVICE_NAME" \
  --region "$AWS_REGION" \
  --query "services[?status!='INACTIVE'].serviceName" \
  --output text 2>/dev/null || echo "")

if [ -z "$EXISTING_SERVICE" ] || [ "$EXISTING_SERVICE" = "None" ]; then
  echo "Service does not exist. Creating service '$SERVICE_NAME'..."
  # Update task definition reference in service file to use full ARN
  sed -i "s|\"taskDefinition\": \"cargo-tracker-task\"|\"taskDefinition\": \"${TASK_DEF_ARN}\"|g" "$SVC_DEF_FILE"
  aws ecs create-service \
    --cli-input-json "file://${SVC_DEF_FILE}" \
    --region "$AWS_REGION"
  echo "Service created."
else
  echo "Service '$SERVICE_NAME' exists. Updating service..."
  aws ecs update-service \
    --cluster "$CLUSTER_NAME" \
    --service "$SERVICE_NAME" \
    --task-definition "$TASK_DEF_ARN" \
    --region "$AWS_REGION" >/dev/null
  echo "Service updated."
fi

rm -f "$SVC_DEF_FILE"

# -------------------------------------------------------------------------
# Wait for service stability
# -------------------------------------------------------------------------
echo ""
echo "Waiting for service to become stable (this may take several minutes)..."
aws ecs wait services-stable \
  --cluster "$CLUSTER_NAME" \
  --services "$SERVICE_NAME" \
  --region "$AWS_REGION"
echo "Service is stable."

# -------------------------------------------------------------------------
# Verify deployment
# -------------------------------------------------------------------------
echo ""
echo "=============================================="
echo "  Deployment Summary"
echo "=============================================="
aws ecs describe-services \
  --cluster "$CLUSTER_NAME" \
  --services "$SERVICE_NAME" \
  --region "$AWS_REGION" \
  --query "services[0].{Status:status,Running:runningCount,Desired:desiredCount,Pending:pendingCount}" \
  --output table

echo ""
echo "CloudWatch Log Group: $LOG_GROUP"
echo "  View logs: aws logs tail $LOG_GROUP --follow --region $AWS_REGION"

if [ -n "$ALB_DNS" ]; then
  echo ""
  echo "Application Load Balancer DNS: http://$ALB_DNS"
  echo "Application URL: http://$ALB_DNS/cargo-tracker"
fi

echo ""
echo "Troubleshooting:"
echo "  List tasks:  aws ecs list-tasks --cluster $CLUSTER_NAME --service-name $SERVICE_NAME --region $AWS_REGION"
echo "  Task logs:   aws logs tail $LOG_GROUP --follow --region $AWS_REGION"
echo "  Task events: aws ecs describe-services --cluster $CLUSTER_NAME --services $SERVICE_NAME --region $AWS_REGION"
echo ""
echo "Deployment complete!"
