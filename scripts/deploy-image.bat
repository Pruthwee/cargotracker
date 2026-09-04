@echo off
setlocal enabledelayedexpansion

REM =============================================================================
REM deploy-image.bat - Deploy Eclipse Cargo Tracker to AWS ECS Fargate
REM =============================================================================

set "SERVICE_NAME=cargo-tracker-service"
set "TASK_FAMILY=cargo-tracker-task"
set "LOG_GROUP=/ecs/cargo-tracker"
set "CONTAINER_NAME=cargo-tracker"
set "CONTAINER_PORT=8080"

echo ==============================================
echo   Eclipse Cargo Tracker - ECS Fargate Deploy
echo ==============================================
echo.

REM -------------------------------------------------------------------------
REM Collect configuration
REM -------------------------------------------------------------------------
set /p "AWS_REGION=Enter AWS Region [us-east-1]: "
if "!AWS_REGION!"=="" set "AWS_REGION=us-east-1"

set /p "CLUSTER_NAME=Enter ECS Cluster name [cargo-tracker-cluster]: "
if "!CLUSTER_NAME!"=="" set "CLUSTER_NAME=cargo-tracker-cluster"

set /p "VPC_ID=Enter VPC ID (e.g., vpc-xxxxxxxx): "

set /p "SUBNETS_INPUT=Enter Subnet IDs (comma-separated, e.g., subnet-aaa,subnet-bbb): "
for /f "tokens=1 delims=," %%a in ("!SUBNETS_INPUT!") do set "SUBNET_1=%%a"
for /f "tokens=2 delims=," %%a in ("!SUBNETS_INPUT!") do set "SUBNET_2=%%a"
if "!SUBNET_2!"=="" set "SUBNET_2=!SUBNET_1!"

REM Trim spaces
for /f "tokens=*" %%a in ("!SUBNET_1!") do set "SUBNET_1=%%a"
for /f "tokens=*" %%a in ("!SUBNET_2!") do set "SUBNET_2=%%a"

set /p "SECURITY_GROUP=Enter Security Group ID (e.g., sg-xxxxxxxx): "
set /p "IMAGE_URI=Enter ECR Image URI (e.g., 123456789.dkr.ecr.us-east-1.amazonaws.com/cargo-tracker:latest): "

REM -------------------------------------------------------------------------
REM Get AWS Account ID
REM -------------------------------------------------------------------------
echo.
echo Retrieving AWS Account ID...
for /f "delims=" %%i in ('aws sts get-caller-identity --query Account --output text 2^>^&1') do set "ACCOUNT_ID=%%i"
if "!ACCOUNT_ID!"=="" (
    echo ERROR: Could not retrieve AWS Account ID. Ensure AWS CLI is configured.
    exit /b 1
)
echo AWS Account ID: !ACCOUNT_ID!

REM -------------------------------------------------------------------------
REM Ensure CloudWatch log group exists
REM -------------------------------------------------------------------------
echo.
echo Ensuring CloudWatch log group '!LOG_GROUP!' exists...
aws logs create-log-group --log-group-name "!LOG_GROUP!" --region "!AWS_REGION!" >nul 2>&1
echo Log group ready: !LOG_GROUP!

REM -------------------------------------------------------------------------
REM Check / create ECS cluster
REM -------------------------------------------------------------------------
echo.
echo Checking ECS cluster '!CLUSTER_NAME!'...
for /f "delims=" %%i in ('aws ecs describe-clusters --clusters "!CLUSTER_NAME!" --region "!AWS_REGION!" --query "clusters[0].status" --output text 2^>^&1') do set "CLUSTER_STATUS=%%i"

if not "!CLUSTER_STATUS!"=="ACTIVE" (
    echo Cluster not found or inactive. Creating cluster '!CLUSTER_NAME!'...
    aws ecs create-cluster --cluster-name "!CLUSTER_NAME!" --region "!AWS_REGION!"
    echo Cluster created.
) else (
    echo Cluster '!CLUSTER_NAME!' is ACTIVE.
)

REM -------------------------------------------------------------------------
REM Load balancer prompt
REM -------------------------------------------------------------------------
echo.
set /p "NEED_LB=Do you need an Application Load Balancer for this service? (y/n) [y]: "
if "!NEED_LB!"=="" set "NEED_LB=y"

set "TARGET_GROUP_ARN="
set "ALB_DNS="

if /i "!NEED_LB!"=="y" (
    echo.
    echo Creating Application Load Balancer and Target Group...

    set "ALB_NAME=cargo-tracker-alb"
    echo Creating ALB '!ALB_NAME!'...
    for /f "delims=" %%i in ('aws elbv2 create-load-balancer --name "!ALB_NAME!" --subnets "!SUBNET_1!" "!SUBNET_2!" --security-groups "!SECURITY_GROUP!" --scheme internet-facing --type application --ip-address-type ipv4 --region "!AWS_REGION!" --query "LoadBalancers[0].LoadBalancerArn" --output text 2^>^&1') do set "ALB_ARN=%%i"
    echo ALB ARN: !ALB_ARN!

    for /f "delims=" %%i in ('aws elbv2 describe-load-balancers --load-balancer-arns "!ALB_ARN!" --region "!AWS_REGION!" --query "LoadBalancers[0].DNSName" --output text 2^>^&1') do set "ALB_DNS=%%i"

    set "TG_NAME=cargo-tracker-tg"
    echo Creating Target Group '!TG_NAME!' (target-type: ip)...
    for /f "delims=" %%i in ('aws elbv2 create-target-group --name "!TG_NAME!" --protocol HTTP --port !CONTAINER_PORT! --vpc-id "!VPC_ID!" --target-type ip --health-check-protocol HTTP --health-check-path "/cargo-tracker/rest/health" --health-check-interval-seconds 30 --health-check-timeout-seconds 10 --healthy-threshold-count 2 --unhealthy-threshold-count 5 --region "!AWS_REGION!" --query "TargetGroups[0].TargetGroupArn" --output text 2^>^&1') do set "TARGET_GROUP_ARN=%%i"
    echo Target Group ARN: !TARGET_GROUP_ARN!

    echo Creating ALB Listener on port 80...
    aws elbv2 create-listener --load-balancer-arn "!ALB_ARN!" --protocol HTTP --port 80 --default-actions "Type=forward,TargetGroupArn=!TARGET_GROUP_ARN!" --region "!AWS_REGION!" >nul
    echo ALB Listener created.
)

REM -------------------------------------------------------------------------
REM Prepare task definition (replace placeholders)
REM -------------------------------------------------------------------------
echo.
echo Preparing task definition...
set "TASK_DEF_FILE=%TEMP%\cargo-tracker-task-def.json"
copy /y "%~dp0..\ecs\task-definition.json" "!TASK_DEF_FILE!" >nul

powershell -Command "(Get-Content '!TASK_DEF_FILE!') -replace '{{ACCOUNT_ID}}','!ACCOUNT_ID!' -replace '{{AWS_REGION}}','!AWS_REGION!' -replace '{{IMAGE_URI}}','!IMAGE_URI!' -replace '{{MEMCACHED_ENDPOINT}}','' -replace '{{DB_JDBC_URL}}','jdbc:h2:file:/app/cargo-tracker-data/cargo-tracker-database' -replace '{{DB_USER}}','' | Set-Content '!TASK_DEF_FILE!'"

REM -------------------------------------------------------------------------
REM Register task definition
REM -------------------------------------------------------------------------
echo Registering ECS task definition...
for /f "delims=" %%i in ('aws ecs register-task-definition --cli-input-json "file://!TASK_DEF_FILE!" --region "!AWS_REGION!" --query "taskDefinition.taskDefinitionArn" --output text 2^>^&1') do set "TASK_DEF_ARN=%%i"
echo Task Definition ARN: !TASK_DEF_ARN!
del /f /q "!TASK_DEF_FILE!" >nul 2>&1

REM -------------------------------------------------------------------------
REM Prepare service definition (replace placeholders)
REM -------------------------------------------------------------------------
echo.
echo Preparing service definition...
set "SVC_DEF_FILE=%TEMP%\cargo-tracker-svc-def.json"
copy /y "%~dp0..\ecs\service-definition.json" "!SVC_DEF_FILE!" >nul

powershell -Command "(Get-Content '!SVC_DEF_FILE!') -replace '{{CLUSTER_NAME}}','!CLUSTER_NAME!' -replace '{{SUBNET_1}}','!SUBNET_1!' -replace '{{SUBNET_2}}','!SUBNET_2!' -replace '{{SECURITY_GROUP}}','!SECURITY_GROUP!' | Set-Content '!SVC_DEF_FILE!'"

REM -------------------------------------------------------------------------
REM Check if service exists and create/update
REM -------------------------------------------------------------------------
echo.
echo Checking if ECS service '!SERVICE_NAME!' exists...
for /f "delims=" %%i in ('aws ecs describe-services --cluster "!CLUSTER_NAME!" --services "!SERVICE_NAME!" --region "!AWS_REGION!" --query "services[?status!='INACTIVE'].serviceName" --output text 2^>^&1') do set "EXISTING_SERVICE=%%i"

if "!EXISTING_SERVICE!"=="" (
    echo Service does not exist. Creating service '!SERVICE_NAME!'...
    powershell -Command "(Get-Content '!SVC_DEF_FILE!') -replace '\"taskDefinition\": \"cargo-tracker-task\"','\"taskDefinition\": \"!TASK_DEF_ARN!\"' | Set-Content '!SVC_DEF_FILE!'"
    aws ecs create-service --cli-input-json "file://!SVC_DEF_FILE!" --region "!AWS_REGION!"
    echo Service created.
) else (
    echo Service '!SERVICE_NAME!' exists. Updating service...
    aws ecs update-service --cluster "!CLUSTER_NAME!" --service "!SERVICE_NAME!" --task-definition "!TASK_DEF_ARN!" --region "!AWS_REGION!" >nul
    echo Service updated.
)

del /f /q "!SVC_DEF_FILE!" >nul 2>&1

REM -------------------------------------------------------------------------
REM Wait for service stability
REM -------------------------------------------------------------------------
echo.
echo Waiting for service to become stable (this may take several minutes)...
aws ecs wait services-stable --cluster "!CLUSTER_NAME!" --services "!SERVICE_NAME!" --region "!AWS_REGION!"
echo Service is stable.

REM -------------------------------------------------------------------------
REM Verify deployment
REM -------------------------------------------------------------------------
echo.
echo ==============================================
echo   Deployment Summary
echo ==============================================
aws ecs describe-services --cluster "!CLUSTER_NAME!" --services "!SERVICE_NAME!" --region "!AWS_REGION!" --query "services[0].{Status:status,Running:runningCount,Desired:desiredCount,Pending:pendingCount}" --output table

echo.
echo CloudWatch Log Group: !LOG_GROUP!
echo   View logs: aws logs tail !LOG_GROUP! --follow --region !AWS_REGION!

if not "!ALB_DNS!"=="" (
    echo.
    echo Application Load Balancer DNS: http://!ALB_DNS!
    echo Application URL: http://!ALB_DNS!/cargo-tracker
)

echo.
echo Troubleshooting:
echo   List tasks:  aws ecs list-tasks --cluster !CLUSTER_NAME! --service-name !SERVICE_NAME! --region !AWS_REGION!
echo   Task logs:   aws logs tail !LOG_GROUP! --follow --region !AWS_REGION!
echo.
echo Deployment complete!

endlocal
