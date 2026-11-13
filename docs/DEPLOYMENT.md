# Deployment Guide — Cargo Tracker on AWS EKS

## Prerequisites and System Requirements

### Local Development
- **Java 11** (JDK 11 or later, e.g., Eclipse Temurin 11)
- **Apache Maven 3.9.x**
- **Docker** (for building container images)
- **AWS CLI** (v2, for ECR and EKS operations)
- **kubectl** (for Kubernetes operations)
- **eksctl** (for EKS cluster management)

### AWS EKS Prerequisites
- An AWS account with permissions for ECR, EKS, and STS
- An EKS cluster running Kubernetes 1.24+
- The AWS Load Balancer Controller installed on the cluster
- An Amazon ECR repository for the container image
- An Amazon RDS PostgreSQL instance (or external PostgreSQL) reachable from the EKS cluster
- IAM permissions for ECR push/pull, EKS cluster access, and secret management

### Database Requirements
The application requires a PostgreSQL database. The database connection details are provided via environment variables:
- `DB_JDBC_URL` — JDBC URL for the PostgreSQL database
- `DB_USER` — Database username
- `DB_PASSWORD` — Database password
- `DB_DRIVER_CLASS` — JDBC driver class (default: `org.postgresql.ds.PGPoolingDataSource`)

### Redis (Optional)
The application uses singleton patterns and local caches that, in a multi-replica deployment, should be externalized to Redis (Amazon ElastiCache) for consistency. See the "Scaling Considerations" section below.

## Local Setup with Docker Compose

### Build and Run Locally

1. Build the application with the cloud profile:
   ```bash
   ./mvnw clean package -Pcloud \
     -DpostgreSqlJdbcUrl="jdbc:postgresql://localhost:5432/postgres" \
     -DpostgreSqlUsername="cargotracker" \
     -DpostgreSqlPassword="cargotracker"
   ```

2. Start the application with Docker Compose:
   ```bash
   docker-compose up
   ```

3. Access the application at `http://localhost:8080`

### Docker Compose Environment Variables

The `docker-compose.yml` file expects the following environment variables:
- `DB_JDBC_URL` — JDBC URL for the database
- `DB_USER` — Database username
- `DB_PASSWORD` — Database password
- `GRAPH_TRAVERSAL_URL` — URL for the graph traversal REST service (defaults to `http://localhost:8080/rest/graph-traversal/shortest-path`)
- `DB_DRIVER_CLASS` — JDBC driver class (defaults to `org.postgresql.ds.PGPoolingDataSource`)

## Building and Pushing the Container Image

### Using build-push.sh (Linux/macOS)

```bash
./scripts/build-push.sh
```

The script will prompt for:
1. AWS region
2. ECR repository name (defaults to `cargo-mono-1`)
3. Image tag (defaults to `latest`)

It will then:
1. Log in to Amazon ECR
2. Create the ECR repository if it doesn't exist
3. Build the Docker image
4. Push the image to ECR

### Using build-push.bat (Windows)

```cmd
scripts\build-push.bat
```

The script follows the same steps as the shell script.

### Docker Image Details

- **Base image**: `amazoncorretto:11` (runtime stage)
- **Build image**: `maven:3.9.4-eclipse-temurin-11` (build stage)
- **Application server**: Payara Micro 6.2025.3
- **Port**: 8080

## AWS EKS Deployment

### Cluster Setup

1. Create an EKS cluster using eksctl:
   ```bash
   eksctl create cluster --name cargo-tracker-cluster --region us-east-1 --nodegroup-name cargo-tracker-nodes --node-type t3.medium --nodes 2 --nodes-max 4
   ```

2. Install the AWS Load Balancer Controller:
   ```bash
   eksctl utils associate-iam-oidc-provider --region us-east-1 --cluster cargo-tracker-cluster --approve
   helm install aws-load-balancer-controller eks/aws-load-balancer-controller \
     -n kube-system \
     -n kube-system \
     --set clusterName=cargo-tracker-cluster \
     --set serviceAccount.create=false \
     --set serviceAccount.name=aws-load-balancer-controller \
     --set region=us-east-1 \
     --set vpcId=<your-vpc-id>
   ```

3. Configure kubectl:
   ```bash
   aws eks update-kubeconfig --region us-east-1 --name cargo-tracker-cluster
   ```

### Deploying the Application

1. Build and push the container image (see above).

2. Run the deploy script:
   ```bash
   ./scripts/deploy-image.sh
   ```

   The script will prompt for:
   - AWS region
   - EKS cluster name
   - Full image URI (from the build-push step)
   - `DB_JDBC_URL` — JDBC URL for the database
   - `GRAPH_TRAVERSAL_URL` — URL for the graph traversal service
   - `DB_USER` — Database username (entered without echo)
   - `DB_PASSWORD` — Database password (entered without echo)

3. The script will:
   - Configure kubectl for the EKS cluster
   - Create the `cargo-tracker` namespace
   - Create a Kubernetes Secret with database credentials
   - Replace placeholders in the deployment manifests
   - Apply the namespace, deployment, service, and ingress
   - Wait for the deployment rollout to complete
   - Display the deployed resources and application URL

### Using deploy-image.bat (Windows)

```cmd
scripts\deploy-image.bat
```

Follow the same prompts as the shell script.

### Deployment Manifests

The Kubernetes manifests are in the `kubernetes/` directory:

- **namespace.yaml** — Creates the `cargo-tracker` namespace
- **deployment.yaml** — Defines the Deployment with 2 replicas, resource limits, liveness/readiness probes, and environment variables
- **service.yaml** — Creates a ClusterIP service on port 80 targeting container port 8080
- **ingress.yaml** — Creates an ALB ingress for the application

### Rolling Updates

To update the application with a new image:
```bash
kubectl set image deployment/cargo-tracker cargo-tracker=<new-image-uri> -n cargo-tracker
kubectl rollout status deployment/cargo-tracker -n cargo-tracker
```

### Rollback

To rollback to a previous revision:
```bash
kubectl rollout history deployment/cargo-tracker -n cargo-tracker
kubectl rollout undo deployment/cargo-tracker -n cargo-tracker
```

## Configuration Management

### Environment Variables

The application reads the following environment variables at runtime:

| Variable | Description | Required | Default |
|---|---|---|---|
| `DB_DRIVER_CLASS` | JDBC driver class name | No | `org.postgresql.ds.PGPoolingDataSource` |
| `DB_JDBC_URL` | JDBC URL for the database | Yes | — |
| `DB_USER` | Database username | Yes | — |
| `DB_PASSWORD` | Database password | Yes | — |
| `GRAPH_TRAVERSAL_URL` | URL for the graph traversal REST service | No | `http://localhost:8080/rest/graph-traversal/shortest-path` |
| `JAVA_OPTS` | JVM options | No | `-Xmx512m -Xms256m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0` |

### Kubernetes Secrets

Database credentials are stored in a Kubernetes Secret named `cargo-tracker-secrets` in the `cargo-tracker` namespace. The secret is created by the deploy script and contains:
- `db-user` — Database username
- `db-password` — Database password

### Configuration in the Dockerfile

The Dockerfile sets default environment variables for local development. Override these at runtime by setting environment variables in your deployment configuration.

## Security Considerations and Best Practices

1. **Database Credentials**: Database credentials are never stored in the container image. They are provided via environment variables or Kubernetes Secrets at runtime.

2. **Non-root User**: The container runs as a non-root user (`cargo-tracker`) for security.

3. **Minimal Base Image**: The runtime stage uses `amazoncorretto:11`, a minimal JDK image.

4. **Resource Limits**: The Kubernetes deployment sets CPU and memory requests and limits to prevent resource exhaustion.

5. **Health Checks**: The application exposes a health check endpoint at `/rest/health` for liveness and readiness probes.

6. **TLS**: The ALB ingress should be configured with TLS certificates for production use.

7. **Network Policies**: Consider applying Kubernetes Network Policies to restrict pod-to-pod communication.

8. **Image Scanning**: Scan container images for vulnerabilities before pushing to ECR.

## Java Notes

### JVM Settings

The container uses the following JVM settings:
- `-Xmx512m` — Maximum heap size of 512 MB
- `-Xms256m` — Initial heap size of 256 MB
- `-XX:+UseContainerSupport` — Enables container-aware JVM settings
- `-XX:MaxRAMPercentage=75.0` — Uses up to 75% of the container's memory limit for the heap

These settings are configured via the `JAVA_OPTS` environment variable and are suitable for the resource limits set in the Kubernetes deployment (512Mi request, 1Gi limit). For higher traffic, increase the memory limits and adjust `-Xmx` accordingly.

### Application Server

The application runs on **Payara Micro 6.2025.3**, which is a self-contained Jakarta EE runtime. The WAR file is deployed directly to Payara Micro at startup using the `--deploy` option.

### Health Endpoint

The application exposes a health check endpoint at `/rest/health` (GET). This endpoint returns:
```json
{"status":"UP"}
```

The Kubernetes deployment uses this endpoint for both liveness and readiness probes:
- **Liveness probe**: HTTP GET to `/rest/health` on port 8080, with a 30-second initial delay and 15-second polling interval.
- **Readiness probe**: HTTP GET to `/rest/health` on port 8080, with a 20-second initial delay and 10-second polling interval.

### Build Profile

The Docker image builds the application using the Maven `cloud` profile (`-Pcloud`), which:
- Uses PostgreSQL as the database driver
- Includes the PostgreSQL JDBC driver in the image
- Configures the application for cloud deployment

### Scaling Considerations

The application uses Jakarta EE singleton patterns (`@Singleton`, `@ApplicationScoped`) and static cache maps for state storage. In a multi-replica deployment on EKS, each pod maintains its own in-memory state, which can lead to inconsistencies. For production multi-replica deployments, consider externalizing state to Amazon ElastiCache for Redis:

1. Provision an Amazon ElastiCache for Redis cluster in the same VPC as the EKS nodes.
2. Add the Redis connection details as environment variables (`REDIS_HOST`, `REDIS_PORT`).
3. Refactor singleton state holders to use Redis for shared state.
4. Replace local caches with Redis-backed caches.

The current single-replica configuration works correctly, but horizontal scaling requires the Redis migration described above.
