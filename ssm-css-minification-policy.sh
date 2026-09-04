#!/usr/bin/env bash
# =============================================================================
# CSS Minification Policy - AWS SSM Parameter Store Setup
# Rule: cz-css-1004 - Unminified CSS in Production Containers
#
# This script stores the CSS minification policy in AWS SSM Parameter Store.
# The ECS Fargate task reads these parameters at runtime to audit and enforce
# minification state without rebuilding images.
#
# Usage:
#   AWS_REGION=us-east-1 AWS_ACCOUNT_ID=123456789012 ./ssm-css-minification-policy.sh
# =============================================================================

set -euo pipefail

AWS_REGION="${AWS_REGION:-us-east-1}"
AWS_ACCOUNT_ID="${AWS_ACCOUNT_ID:-}"
SSM_PATH_PREFIX="/cargo-tracker/css"

echo "==> Storing CSS minification policy in SSM Parameter Store..."
echo "    Region : ${AWS_REGION}"
echo "    Prefix : ${SSM_PATH_PREFIX}"

# ---------------------------------------------------------------------------
# Parameter 1: CSS minification required flag
# Ops teams can set this to "false" to temporarily disable enforcement
# without rebuilding the container image.
# ---------------------------------------------------------------------------
aws ssm put-parameter \
  --region "${AWS_REGION}" \
  --name "${SSM_PATH_PREFIX}/minification-policy" \
  --description "CSS minification enforcement policy for cargo-tracker (cz-css-1004)" \
  --value '{"required":true,"enforced":true,"auditMode":false}' \
  --type "String" \
  --overwrite

echo "    [OK] ${SSM_PATH_PREFIX}/minification-policy stored"

# ---------------------------------------------------------------------------
# Parameter 2: Maximum unminified CSS file size threshold (bytes)
# Files exceeding this threshold in production containers trigger a policy
# violation alert. Default: 10240 bytes (10 KB).
# ---------------------------------------------------------------------------
aws ssm put-parameter \
  --region "${AWS_REGION}" \
  --name "${SSM_PATH_PREFIX}/max-file-size-threshold" \
  --description "Max allowed unminified CSS file size in bytes for cargo-tracker (cz-css-1004)" \
  --value "10240" \
  --type "String" \
  --overwrite

echo "    [OK] ${SSM_PATH_PREFIX}/max-file-size-threshold stored"

echo ""
echo "==> SSM parameters stored successfully."
echo ""
echo "    To verify:"
echo "    aws ssm get-parameters-by-path --path '${SSM_PATH_PREFIX}' --region '${AWS_REGION}'"
echo ""
echo "    ECS Fargate tasks will inject these values as environment variables:"
echo "      CSS_MINIFICATION_POLICY          <- ${SSM_PATH_PREFIX}/minification-policy"
echo "      CSS_MAX_FILE_SIZE_THRESHOLD      <- ${SSM_PATH_PREFIX}/max-file-size-threshold"
