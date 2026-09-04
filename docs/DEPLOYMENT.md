# Eclipse Cargo Tracker - AWS ECS Fargate Deployment Guide

## Overview

This guide covers the complete deployment of the **Eclipse Cargo Tracker** application to **AWS ECS Fargate**. The application is a Jakarta EE 10 reference implementation demonstrating Domain-Driven Design (DDD) patterns, running on **Payara Micro** with **Java 11**.

---

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Project Structure](#project-structure)
3. [Local Development with Docker Compose](#local-development-with-docker-compose)
4. [Build and Push Docker Image](#build-and-push-docker-image)
5. [AWS ECS Fargate Prerequisites](#aws-ecs-fargate-prerequisites)
6. [ECS Task Definition Explained](#ecs-task-definition-explained)
7. [ECS Service Configuration](#ecs-service-configuration)
8. [ECS Fargate Deployment Walkthrough](#ecs-fargate-deployment-walkthrough)
9. [ECS-Specific Troubleshooting](#ecs-specific-troubleshooting)
10. [ECS Fargate Scaling and Management](#ecs-fargate-scaling-and-management)
11. [Configuration Management](#configuration-management)
12. [Security Considerations](#security-considerations)
13. [Technology-Specific Notes](#technology-specific-notes)

---

## Prerequisites

### Local Development Requirements
- **Docker** 20.10+ and **Docker Compose** v2+
- **Java 11** (Eclipse Temurin recommended)
- **Maven 3.9+**
- **AWS CLI** v2 configured with appropriate permissions

### AWS Requirements
- AWS Account with appropriate IAM permissions
- AWS CLI v2 installed and configured (`aws configure`)
- VPC with at least 2 subnets (for high availability)
- Security groups configured for the application

---

## Project Structure

```
cargo-tracker-3/
├── Dockerfile                    # Multi-stage build (Maven builder + eclipse-temurin:11-jdk runtime)
├── docker-compose.yml            # Local development (application only)
├── .dockerignore                 # Excludes build artifacts and wrapper files
├── pom.xml                       # Maven build configuration (Java 11, Jakarta EE 10)
├── post-boot-commands.asadmin    # Payara Micro post-boot configuration
├── src/                          # Application source code
├── ecs/
│   ├── task-definition.json      # ECS Fargate task definition
│   └── service-definition.json   # ECS Fargate service definition
├── scripts/
│   ├── build-push.sh             # Linux/macOS: build and push to ECR/Docker Hub
│   ├── build-push.bat            # Windows: build and push to ECR/Docker Hub
│   ├── deploy-image.sh           # Linux/macOS: deploy to ECS Fargate
│   └── deploy-image.bat          # Windows: deploy to ECS Fargate
└── docs/
    └── DEPLOYMENT.md             # This file
```

---

## Local Development with Docker Compose

### Quick Start

```bash
# Build and start the application
docker compose up --build

# Run in background
docker compose up -d --build

# View logs
docker compose logs -f cargo-tracker

# Stop the application
docker compose down
```

### Access the Application

- **Application**: http://localhost:8080/cargo-tracker
- **Tracking**: http://localhost:8080/cargo-tracker/public/track.xhtml
- **Admin**: http://localhost:8080/cargo-tracker/admin/

### Environment Variables for Local Development

Create a `.env` file in the project root to override defaults:

```env
# Database (H2 file-based by default)
DB_DRIVER_CLASS=org.h2.jdbcx.JdbcDataSource
DB_JDBC_URL=jdbc:h2:file:/app/cargo-tracker-data/cargo-tracker-database
DB_USER=
DB_PASSWORD=

# For PostgreSQL (cloud profile)
# DB_DRIVER_CLASS=org.postgresql.ds.PGPoolingDataSource
# DB_JDBC_URL=jdbc:postgresql://your-db-host:5432/cargotracker
# DB_USER=postgres
# DB_PASSWORD=your-password

# Graph traversal service
GRAPH_TRAVERSAL_URL=http://localhost:8080/cargo-tracker/rest/graph-traversal/shortest-path

# Memcached (optional, for ElastiCache integration)
MEMCACHED_ENDPOINT=

# CSS minification policy
CSS_MINIFICATION_REQUIRED=true
CSS_MAX_UNMINIFIED_SIZE_BYTES=10240
```

---

## Build and Push Docker Image

### Linux/macOS

```bash
# Make the script executable
chmod +x scripts/build-push.sh

# Run the build and push script
./scripts/build-push.sh
```

The script will prompt you to:
1. Enter an image tag (default: `latest`)
2. Select registry type (1=AWS ECR, 2=Docker Hub)
3. Provide registry credentials and details

### Windows

```cmd
scripts\build-push.bat
```

### Manual Build

```bash
# Build the image
docker build -t cargo-tracker:latest .

# Tag for ECR
docker tag cargo-tracker:latest 123456789.dkr.ecr.us-east-1.amazonaws.com/cargo-tracker:latest

# Login to ECR
aws ecr get-login-password --region us-east-1 | docker login --username AWS --password-stdin 123456789.dkr.ecr.us-east-1.amazonaws.com

# Push to ECR
docker push 123456789.dkr.ecr.us-east-1.amazonaws.com/cargo-tracker:latest
```

---

## AWS ECS Fargate Prerequisites

### 1. IAM Roles

#### ECS Task Execution Role
Required for ECS to pull images from ECR and write logs to CloudWatch:

```bash
# Create the execution role
aws iam create-role \
  --role-name ecsTaskExecutionRole \
  --assume-role-policy-document '{
    "Version": "2012-10-17",
    "Statement": [{
      "Effect": "Allow",
      "Principal": {"Service": "ecs-tasks.amazonaws.com"},
      "Action": "sts:AssumeRole"
    }]
  }'

# Attach the managed policy
aws iam attach-role-policy \
  --role-name ecsTaskExecutionRole \
  --policy-arn arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy

# Add SSM Parameter Store access (for secrets)
aws iam attach-role-policy \
  --role-name ecsTaskExecutionRole \
  --policy-arn arn:aws:iam::aws:policy/AmazonSSMReadOnlyAccess
```

#### ECS Task Role (Optional)
For the application to access AWS services (S3, DynamoDB, etc.):

```bash
aws iam create-role \
  --role-name ecsTaskRole \
  --assume-role-policy-document '{
    "Version": "2012-10-17",
    "Statement": [{
      "Effect": "Allow",
      "Principal": {"Service": "ecs-tasks.amazonaws.com"},
      "Action": "sts:AssumeRole"
    }]
  }'
```

### 2. VPC and Networking

```bash
# List available VPCs
aws ec2 describe-vpcs --query "Vpcs[*].{VpcId:VpcId,CIDR:CidrBlock,Default:IsDefault}"

# List subnets (choose at least 2 in different AZs)
aws ec2 describe-subnets \
  --filters "Name=vpc-id,Values=vpc-xxxxxxxx" \
  --query "Subnets[*].{SubnetId:SubnetId,AZ:AvailabilityZone,CIDR:CidrBlock}"
```

### 3. Security Group

Create a security group allowing inbound traffic on port 8080:

```bash
# Create security group
SG_ID=$(aws ec2 create-security-group \
  --group-name cargo-tracker-sg \
  --description "Security group for Cargo Tracker ECS tasks" \
  --vpc-id vpc-xxxxxxxx \
  --query "GroupId" --output text)

# Allow inbound HTTP on port 8080
aws ec2 authorize-security-group-ingress \
  --group-id $SG_ID \
  --protocol tcp \
  --port 8080 \
  --cidr 0.0.0.0/0

# Allow inbound HTTP on port 80 (for ALB)
aws ec2 authorize-security-group-ingress \
  --group-id $SG_ID \
  --protocol tcp \
  --port 80 \
  --cidr 0.0.0.0/0

echo "Security Group ID: $SG_ID"
```

### 4. CloudWatch Log Group

```bash
aws logs create-log-group \
  --log-group-name /ecs/cargo-tracker \
  --region us-east-1

# Set retention policy (optional)
aws logs put-retention-policy \
  --log-group-name /ecs/cargo-tracker \
  --retention-in-days 30
```

### 5. SSM Parameter Store (for Secrets)

```bash
# Store database password
aws ssm put-parameter \
  --name "/cargo-tracker/db/password" \
  --value "your-db-password" \
  --type SecureString \
  --region us-east-1

# Store CSS minification policy
aws ssm put-parameter \
  --name "/cargo-tracker/css/minification-policy" \
  --value "enforce" \
  --type String \
  --region us-east-1

aws ssm put-parameter \
  --name "/cargo-tracker/css/max-file-size-threshold" \
  --value "10240" \
  --type String \
  --region us-east-1
```

---

## ECS Task Definition Explained

The task definition (`ecs/task-definition.json`) configures how the container runs on Fargate:

### Key Configuration

| Parameter | Value | Description |
|-----------|-------|-------------|
| `family` | `cargo-tracker-task` | Task definition family name |
| `requiresCompatibilities` | `["FARGATE"]` | Fargate launch type |
| `networkMode` | `awsvpc` | Required for Fargate |
| `cpu` | `"512"` | 0.5 vCPU |
| `memory` | `"1024"` | 1 GB RAM |
| `containerPort` | `8080` | Payara Micro HTTP port |

### Valid Fargate CPU/Memory Combinations

| CPU | Valid Memory Options |
|-----|---------------------|
| 256 (.25 vCPU) | 512, 1024, 2048 MB |
| **512 (.5 vCPU)** | **1024, 2048, 3072, 4096 MB** ← Used |
| 1024 (1 vCPU) | 2048–8192 MB |
| 2048 (2 vCPU) | 4096–16384 MB |
| 4096 (4 vCPU) | 8192–30720 MB |

### JVM Configuration

The task definition sets `JAVA_OPTS` with container-aware JVM flags:
```
-Xmx512m -Xms256m
-XX:+UseContainerSupport          # Respect container memory limits
-XX:MaxRAMPercentage=75.0         # Use 75% of container memory for heap
-XX:+UnlockExperimentalVMOptions  # Enable experimental JVM features
-Djava.net.preferIPv4Stack=true   # Use IPv4 (important in containers)
-Dfile.encoding=UTF-8
-Duser.timezone=UTC
```

### Logging Configuration

All container logs are sent to CloudWatch Logs:
- **Log Group**: `/ecs/cargo-tracker`
- **Log Driver**: `awslogs`
- **Stream Prefix**: `ecs`

---

## ECS Service Configuration

The service definition (`ecs/service-definition.json`) manages how tasks are scheduled:

### Key Configuration

| Parameter | Value | Description |
|-----------|-------|-------------|
| `launchType` | `FARGATE` | Serverless container execution |
| `desiredCount` | `2` | Run 2 tasks for high availability |
| `networkMode` | `awsvpc` | Each task gets its own ENI |
| `assignPublicIp` | `ENABLED` | Tasks get public IPs (for ECR access) |
| `maximumPercent` | `200` | Allow 200% tasks during rolling deploy |
| `minimumHealthyPercent` | `50` | Keep 50% healthy during deploy |

---

## ECS Fargate Deployment Walkthrough

### Step 1: Build and Push Image

```bash
chmod +x scripts/build-push.sh
./scripts/build-push.sh
# Select: 1 (AWS ECR)
# Enter: AWS Region, ECR repo name
# Script auto-creates ECR repo if needed
```

### Step 2: Deploy to ECS Fargate

```bash
chmod +x scripts/deploy-image.sh
./scripts/deploy-image.sh
```

The script will prompt for:
- AWS Region
- ECS Cluster name (auto-created if not exists)
- VPC ID
- Subnet IDs (comma-separated)
- Security Group ID
- ECR Image URI
- Whether to create an Application Load Balancer

### Step 3: Verify Deployment

```bash
# Check service status
aws ecs describe-services \
  --cluster cargo-tracker-cluster \
  --services cargo-tracker-service \
  --region us-east-1

# List running tasks
aws ecs list-tasks \
  --cluster cargo-tracker-cluster \
  --service-name cargo-tracker-service \
  --region us-east-1

# View application logs
aws logs tail /ecs/cargo-tracker --follow --region us-east-1
```

### Step 4: Access the Application

If using ALB:
```
http://<ALB-DNS-NAME>/cargo-tracker
```

If accessing directly (public IP):
```
http://<TASK-PUBLIC-IP>:8080/cargo-tracker
```

---

## ECS-Specific Troubleshooting

### Task Fails to Start

```bash
# Check stopped task reason
aws ecs describe-tasks \
  --cluster cargo-tracker-cluster \
  --tasks <task-arn> \
  --region us-east-1 \
  --query "tasks[0].{Status:lastStatus,StopReason:stoppedReason,Containers:containers[*].{Name:name,Reason:reason,ExitCode:exitCode}}"
```

**Common causes:**
- `CannotPullContainerError`: ECR permissions issue → Check `ecsTaskExecutionRole` has ECR pull permissions
- `OutOfMemoryError`: Increase task memory → Use cpu:"512", memory:"2048"
- `ResourceInitializationError`: Execution role missing → Attach `AmazonECSTaskExecutionRolePolicy`

### Network Issues

```bash
# Verify security group allows port 8080
aws ec2 describe-security-groups \
  --group-ids sg-xxxxxxxx \
  --query "SecurityGroups[0].IpPermissions"

# Check task network interface
aws ecs describe-tasks \
  --cluster cargo-tracker-cluster \
  --tasks <task-arn> \
  --query "tasks[0].attachments"
```

### CPU/Memory Errors

```
InvalidParameterException: Invalid CPU or Memory value
```
**Fix**: Use valid Fargate combinations. Default: `cpu: "512"`, `memory: "1024"`

### Payara Micro Startup Issues

Payara Micro can take 60-120 seconds to start. If health checks fail:
1. Increase `healthCheckGracePeriodSeconds` to 300 in service definition
2. Check logs: `aws logs tail /ecs/cargo-tracker --follow`
3. Verify the WAR deployed correctly in logs

### SSM Parameter Access Denied

```bash
# Verify execution role has SSM access
aws iam list-attached-role-policies --role-name ecsTaskExecutionRole

# Add SSM policy if missing
aws iam attach-role-policy \
  --role-name ecsTaskExecutionRole \
  --policy-arn arn:aws:iam::aws:policy/AmazonSSMReadOnlyAccess
```

---

## ECS Fargate Scaling and Management

### Manual Scaling

```bash
# Scale to 4 tasks
aws ecs update-service \
  --cluster cargo-tracker-cluster \
  --service cargo-tracker-service \
  --desired-count 4 \
  --region us-east-1
```

### Auto Scaling

```bash
# Register scalable target
aws application-autoscaling register-scalable-target \
  --service-namespace ecs \
  --resource-id service/cargo-tracker-cluster/cargo-tracker-service \
  --scalable-dimension ecs:service:DesiredCount \
  --min-capacity 2 \
  --max-capacity 10

# Create CPU-based scaling policy
aws application-autoscaling put-scaling-policy \
  --service-namespace ecs \
  --resource-id service/cargo-tracker-cluster/cargo-tracker-service \
  --scalable-dimension ecs:service:DesiredCount \
  --policy-name cargo-tracker-cpu-scaling \
  --policy-type TargetTrackingScaling \
  --target-tracking-scaling-policy-configuration '{
    "TargetValue": 70.0,
    "PredefinedMetricSpecification": {
      "PredefinedMetricType": "ECSServiceAverageCPUUtilization"
    },
    "ScaleInCooldown": 300,
    "ScaleOutCooldown": 60
  }'
```

### Blue/Green Deployment

For zero-downtime deployments, use AWS CodeDeploy with ECS:

```bash
# Update service with new task definition (rolling update)
aws ecs update-service \
  --cluster cargo-tracker-cluster \
  --service cargo-tracker-service \
  --task-definition cargo-tracker-task:NEW_REVISION \
  --deployment-configuration "maximumPercent=200,minimumHealthyPercent=100" \
  --region us-east-1
```

### Force New Deployment

```bash
aws ecs update-service \
  --cluster cargo-tracker-cluster \
  --service cargo-tracker-service \
  --force-new-deployment \
  --region us-east-1
```

---

## Configuration Management

### Environment Variables

The application uses the following environment variables (set in task definition):

| Variable | Description | Default |
|----------|-------------|---------|
| `JAVA_OPTS` | JVM options | `-Xmx512m -Xms256m ...` |
| `TZ` | Timezone | `UTC` |
| `DB_DRIVER_CLASS` | JDBC driver class | H2 (dev), PostgreSQL (cloud) |
| `DB_JDBC_URL` | Database JDBC URL | H2 file-based |
| `DB_USER` | Database username | (empty) |
| `DB_PASSWORD` | Database password | Via SSM SecureString |
| `GRAPH_TRAVERSAL_URL` | Graph traversal service URL | localhost |
| `MEMCACHED_ENDPOINT` | ElastiCache Memcached endpoint | (empty) |
| `CSS_MINIFICATION_REQUIRED` | CSS minification policy | `true` |
| `CSS_MAX_UNMINIFIED_SIZE_BYTES` | Max unminified CSS size | `10240` |

### Updating Configuration

```bash
# Update an environment variable in the task definition
# 1. Get current task definition
aws ecs describe-task-definition \
  --task-definition cargo-tracker-task \
  --query "taskDefinition" > /tmp/task-def.json

# 2. Edit the environment variables in /tmp/task-def.json

# 3. Register new revision
aws ecs register-task-definition \
  --cli-input-json file:///tmp/task-def.json

# 4. Update service to use new revision
aws ecs update-service \
  --cluster cargo-tracker-cluster \
  --service cargo-tracker-service \
  --task-definition cargo-tracker-task
```

---

## Security Considerations

### Container Security
- ✅ Application runs as non-root user (`cargotracker`)
- ✅ No unnecessary packages installed in runtime image
- ✅ Secrets stored in AWS SSM Parameter Store (not environment variables)
- ✅ Database passwords retrieved via SSM SecureString

### Network Security
- Use private subnets for ECS tasks when possible
- Place ALB in public subnets, ECS tasks in private subnets
- Configure security groups to allow only necessary traffic
- Enable VPC Flow Logs for network monitoring

### IAM Best Practices
- Use least-privilege IAM policies
- Separate execution role and task role
- Rotate credentials regularly
- Enable CloudTrail for API auditing

### Image Security
- Use specific image tags (not `latest`) in production
- Scan images with Amazon ECR image scanning
- Enable ECR lifecycle policies to remove old images

```bash
# Enable ECR image scanning
aws ecr put-image-scanning-configuration \
  --repository-name cargo-tracker \
  --image-scanning-configuration scanOnPush=true

# Set lifecycle policy (keep last 10 images)
aws ecr put-lifecycle-policy \
  --repository-name cargo-tracker \
  --lifecycle-policy-text '{
    "rules": [{
      "rulePriority": 1,
      "description": "Keep last 10 images",
      "selection": {
        "tagStatus": "any",
        "countType": "imageCountMoreThan",
        "countNumber": 10
      },
      "action": {"type": "expire"}
    }]
  }'
```

---

## Technology-Specific Notes

### Jakarta EE 10 on Payara Micro

This application uses **Jakarta EE 10** APIs running on **Payara Micro 6.x**:
- **CDI 4.0** for dependency injection
- **Jakarta Faces 4.0** (PrimeFaces 14) for UI
- **Jakarta Persistence 3.1** (JPA) for data access
- **Jakarta Messaging 3.1** (JMS) for async messaging
- **Jakarta RESTful Web Services 3.1** (JAX-RS) for REST APIs
- **Jakarta Batch 2.1** for batch processing

### Payara Micro Startup Time

Payara Micro typically takes **60-120 seconds** to fully start. Configure ECS health check grace period accordingly:
```json
"healthCheckGracePeriodSeconds": 300
```

### H2 vs PostgreSQL

- **Development/Local**: H2 file-based database (default)
- **Production/Cloud**: PostgreSQL via `cloud` Maven profile
  - Build with: `mvn clean package -Pcloud -DpostgreSqlJdbcUrl=... -DpostgreSqlUsername=... -DpostgreSqlPassword=...`
  - Or set `DB_DRIVER_CLASS`, `DB_JDBC_URL`, `DB_USER`, `DB_PASSWORD` environment variables

### ElastiCache Memcached Integration

The application includes `spymemcached` client for distributed caching:
- Set `MEMCACHED_ENDPOINT` environment variable to your ElastiCache endpoint
- Format: `your-cluster.abc123.cfg.use1.cache.amazonaws.com:11211`

### CSS Minification Policy

The build pipeline enforces CSS minification via `yuicompressor-maven-plugin`:
- CSS files in `resources/css/` and `resources/leaflet/` are minified during build
- Policy enforced via `CSS_MINIFICATION_REQUIRED` environment variable
- SSM Parameter Store path: `/cargo-tracker/css/minification-policy`

### JVM Tuning for Containers

For production workloads, consider increasing resources:
```json
{
  "cpu": "1024",
  "memory": "2048"
}
```
And update `JAVA_OPTS`:
```
-Xmx1536m -Xms512m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0
```

### Monitoring with CloudWatch

```bash
# Create CloudWatch dashboard
aws cloudwatch put-dashboard \
  --dashboard-name cargo-tracker \
  --dashboard-body '{
    "widgets": [{
      "type": "metric",
      "properties": {
        "metrics": [
          ["AWS/ECS", "CPUUtilization", "ServiceName", "cargo-tracker-service", "ClusterName", "cargo-tracker-cluster"],
          ["AWS/ECS", "MemoryUtilization", "ServiceName", "cargo-tracker-service", "ClusterName", "cargo-tracker-cluster"]
        ],
        "period": 300,
        "title": "Cargo Tracker ECS Metrics"
      }
    }]
  }'
```
