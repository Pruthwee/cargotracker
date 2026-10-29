# cargotrackerM-Mono: container and AWS EKS deployment guide

Eclipse Cargo Tracker is a Jakarta EE 10 web application (JSF/PrimeFaces, Jakarta REST, EJB, JPA, JMS) packaged as a WAR and built with Maven on Java 11. The container runs it on **Payara Micro** (the Jakarta EE runtime the project's `payara`/`cloud` profiles target) on top of `amazoncorretto:11`, as a non-root user, listening on port **8080**.

Files added for the container: `Dockerfile`, `.dockerignore`, `docker-compose.yml`, `kubernetes/*.yaml`, `scripts/build-push.sh|.bat`, `scripts/deploy-image.sh|.bat` and this guide.

## 1. Prerequisites and system requirements

| Tool | Use |
| --- | --- |
| Docker 20.10+ (with BuildKit) | build and run the image |
| AWS CLI v2 | ECR login, `aws eks update-kubeconfig` |
| kubectl (within one minor version of the cluster) | deploy to EKS |
| eksctl | create the cluster (optional) |
| An IAM user or role | `ecr:*` on the repository, `eks:DescribeCluster`, and a mapping in the cluster (`aws-auth` ConfigMap or EKS access entry) |
| A PostgreSQL 12+ database (for example Amazon RDS) | the application's data; reachable from the pods |

Resources: 2 pods x (250m CPU / 512Mi requested, 500m CPU / 1Gi limit). The build needs about 2 GB of RAM and network access to Maven Central.

## 2. Local setup with Docker Compose

`docker-compose.yml` starts only the application. The database is external, and every setting reaches the container through environment variables taken from the shell that runs compose:

```bash
export DB_JDBC_URL=jdbc:postgresql://host.docker.internal:5432/postgres
export DB_USER=<database user>
export DB_PASSWORD=<database password>
docker compose up --build
```

The application is then at http://localhost:8080/ and the health check at http://localhost:8080/rest/health. The named volume `payara-runtime` holds `/tmp`, where Payara Micro unpacks the application.

## 3. Build and push the image

Run both scripts from the repository root.

* Linux or macOS: `./scripts/build-push.sh`
* Windows: `scripts\build-push.bat`

They ask for the registry (1. AWS ECR, 2. Docker Hub), then for that registry's details (ECR: region, account ID, repository; Docker Hub: user, password or token, repository), then for `IMAGE_TAG` (empty means `latest`). They run `docker build -f Dockerfile -t <registry>/<repo>:<tag> .`, log in (`aws ecr get-login-password ... | docker login --username AWS --password-stdin`), create the ECR repository when it is missing, and push.

## 4. Deploy to AWS EKS

### 4.1 Cluster setup and kubectl configuration

```bash
eksctl create cluster --name cargotrackerm-mono --region us-east-1 --nodegroup-name workers --node-type t3.medium --nodes 2
aws eks update-kubeconfig --region us-east-1 --name cargotrackerm-mono
kubectl cluster-info
```

The Ingress needs the **AWS Load Balancer Controller** in the cluster (install it with Helm, with an IAM role for its service account; see the AWS documentation). The nodes must be able to pull from ECR (the default node role has `AmazonEC2ContainerRegistryReadOnly`) and to reach the database (security group rule for port 5432).

### 4.2 Deploy with the script

```bash
./scripts/deploy-image.sh      # Windows: scripts\deploy-image.bat
```

It asks for the AWS region, the cluster name and the image URI, for `DB_JDBC_URL`, `DB_USER` and `DB_PASSWORD` (the password is read without echo), runs `aws eks update-kubeconfig`, checks `kubectl cluster-info`, fills the placeholders (`{{IMAGE_URI}}`, `{{NAMESPACE}}`, `{{DB_JDBC_URL}}`) in the manifests with `sed` (PowerShell in the .bat), applies `namespace.yaml`, creates the Secret `cargotrackerm-mono-secrets`, applies `deployment.yaml`, `service.yaml` and `ingress.yaml`, waits with `kubectl rollout status deployment/cargotrackerm-mono -n cargotrackerm-mono`, shows `kubectl get pods,svc,ingress -n cargotrackerm-mono` and the application's URL. The manifests in `kubernetes/` are restored to their placeholder form when the script ends.

### 4.3 The manifests

| File | Content |
| --- | --- |
| `kubernetes/namespace.yaml` | Namespace `cargotrackerm-mono`, label `name: cargotrackerm-mono` |
| `kubernetes/deployment.yaml` | Deployment `cargotrackerm-mono`, 2 replicas, container port 8080, image `"{{IMAGE_URI}}"`, `DB_JDBC_URL` from a placeholder, `DB_USER` and `DB_PASSWORD` from the Secret `cargotrackerm-mono-secrets`, requests 250m / 512Mi and limits 500m / 1Gi, liveness and readiness probes on `/rest/health` |
| `kubernetes/service.yaml` | Service `cargotrackerm-mono-service`, ClusterIP, port 80 to container port 8080 |
| `kubernetes/ingress.yaml` | Ingress `cargotrackerm-mono-ingress` for host `cargotrackerm-mono.example.com`, path `/`, with the ALB annotations (`kubernetes.io/ingress.class: alb`, `alb.ingress.kubernetes.io/scheme: internet-facing`, target type `ip`, health check path `/rest/health`) |

Change the host in `ingress.yaml` to your own domain, or call the load balancer's address directly with a `Host` header.

### 4.4 Troubleshooting

* **Pods not starting.** `kubectl get pods -n cargotrackerm-mono`, `kubectl describe pod <pod> -n cargotrackerm-mono`, `kubectl logs <pod> -n cargotrackerm-mono`. `ImagePullBackOff`: wrong image URI or the node role cannot read ECR. `CrashLoopBackOff`: read the log; a wrong `DB_JDBC_URL`, `DB_USER` or `DB_PASSWORD` shows as a data source or JPA error at deployment. `OOMKilled`: raise the memory limit and `-Xmx` together. Probe failures during start-up: Payara Micro needs 30 to 60 seconds; raise `initialDelaySeconds`.
* **Service problems.** `kubectl get endpoints cargotrackerm-mono-service -n cargotrackerm-mono` must list the pod IPs; if empty, the pods are not ready or the selector `app: cargotrackerm-mono` does not match. Test from inside with `kubectl port-forward svc/cargotrackerm-mono-service 8080:80 -n cargotrackerm-mono`.
* **Ingress problems.** `kubectl describe ingress cargotrackerm-mono-ingress -n cargotrackerm-mono`; no ADDRESS means the AWS Load Balancer Controller is missing or lacks IAM permissions (`kubectl logs -n kube-system deployment/aws-load-balancer-controller`). Unhealthy targets: check that `/rest/health` answers 200 and the security groups allow the ALB to reach the pods.
* **Database.** Test the connection from a pod's network: the RDS security group must allow port 5432 from the node security group.

### 4.5 Scaling and management

```bash
kubectl scale deployment/cargotrackerm-mono --replicas=4 -n cargotrackerm-mono
kubectl autoscale deployment cargotrackerm-mono --cpu-percent=70 --min=2 --max=10 -n cargotrackerm-mono   # HPA, needs metrics-server
kubectl set image deployment/cargotrackerm-mono cargotrackerm-mono=<new image URI> -n cargotrackerm-mono   # rolling update
kubectl rollout status deployment/cargotrackerm-mono -n cargotrackerm-mono
kubectl rollout history deployment/cargotrackerm-mono -n cargotrackerm-mono
kubectl rollout undo deployment/cargotrackerm-mono -n cargotrackerm-mono                                  # rollback
```

Scaling note: the application keeps some state in each pod's memory (the SSE broadcaster of connected browsers, CDI application-scoped beans, the JPA shared cache `shared-cache-mode=ALL`, and `ObservesAsync` events). With several replicas, real-time tracking events fire only on the pod that handled the change, and the JPA second-level cache of one pod does not see changes made through another. The analysis flagged these points (cz-java-0064, cz-java-0070); they were not changed (see the report). Run with the reasoning above in mind, or with `replicas: 1`, until they are addressed.

## 5. Configuration management and environment variables

| Variable | Kind | Meaning |
| --- | --- | --- |
| `DB_JDBC_URL` | plain value (Deployment placeholder `{{DB_JDBC_URL}}`) | JDBC URL of PostgreSQL, for example `jdbc:postgresql://<rds-endpoint>:5432/postgres` |
| `DB_USER` | Kubernetes Secret `cargotrackerm-mono-secrets`, key `DB_USER` | database user |
| `DB_PASSWORD` | Kubernetes Secret `cargotrackerm-mono-secrets`, key `DB_PASSWORD` | database password |
| `JAVA_OPTS` | optional | JVM options, see section 7 |
| `TZ` | image default `UTC` | time zone |

How the data source is configured: `src/main/webapp/WEB-INF/web.xml` defines the data source with Maven properties filtered at build time. The `Dockerfile` builds with the `cloud` profile and passes the Payara variables `${ENV=DB_JDBC_URL}`, `${ENV=DB_USER}` and `${ENV=DB_PASSWORD}` as those properties, so the WAR holds only the variable references and Payara resolves them from the container's environment at start-up. No credential is in the image. The JMS queues are the embedded ones of the runtime.

## 6. Security considerations and best practices

* Credentials are only in the Kubernetes Secret (created by the deploy script from hidden input) or in the shell environment for compose; never in the image, the manifests or the repository. For production prefer AWS Secrets Manager with the External Secrets Operator, and IAM Roles for Service Accounts (IRSA) for any AWS access.
* The container runs as the non-root user 1001; the runtime image has no package manager step and no extra tools.
* Use a private ECR repository, enable image scanning, and pin image tags (not `latest`) in production.
* Put TLS on the ALB (ACM certificate annotation `alb.ingress.kubernetes.io/certificate-arn` and `listen-ports`), restrict the database security group to the nodes, and add a NetworkPolicy.
* `web.xml` sets `jakarta.faces.PROJECT_STAGE` to `Development` and `persistence.xml` sets EclipseLink logging to `FINEST` and schema generation to `create`; review them for production (they were left as the project had them).
* Rotate the database password by updating the Secret and restarting the Deployment (`kubectl rollout restart`).

## 7. Java notes

* **JVM settings.** `JAVA_OPTS` defaults to `-Xmx512m -Xms256m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0` plus UTF-8 file encoding, UTC time zone and `/dev/urandom` as entropy source. The fixed `-Xmx512m` leaves room inside the 1Gi limit for metaspace, thread stacks and Payara's native memory; `MaxRAMPercentage` only applies if `-Xmx` is removed. To use more memory, raise the limit first and then `-Xmx` (keep the heap near 50 to 60 percent of the limit for Payara Micro). Change it in the Deployment `env` (add `JAVA_OPTS`) or in compose.
* **Start command.** `java -jar payara-micro.jar --nocluster --port 8080 --addLibs postgresql.jar --deploy ROOT.war` through `sh -c "exec java ..."`, so java is the container's first process and gets SIGTERM for a graceful shutdown. The WAR is deployed as `ROOT.war`, which gives the context root `/` (the `cloud` profile expects it: `http://localhost:8080/rest/graph-traversal/shortest-path`). `--nocluster` turns off Hazelcast clustering, which does not work across pods without extra setup.
* **Spring profiles.** None: this is a Jakarta EE application, not Spring. The environments are Maven profiles (`payara` with H2 for local runs, `glassfish`, `cloud` with PostgreSQL used by the image, `openliberty`).
* **Health endpoints.** The application had none, so `HealthService` was added (`org.eclipse.cargotracker.interfaces.health.HealthService`): `GET /rest/health` answers `{"status":"UP"}` with HTTP 200 as soon as the application is deployed. The Deployment's liveness and readiness probes and the ALB health check use it. It does not check the database.
* **Build.** `pom.xml` pins `maven-compiler-plugin` 3.11.0 and `maven-surefire-plugin` ${surefire.version} so that older Maven versions honour `maven.compiler.release` 11 and run the JUnit 5 tests; the Dockerfile uses `maven:3.9.4-eclipse-temurin-11`.
* **Not verified here.** The image was not built and Payara Micro was not started with this command line (no Docker in the build environment); the Maven build, the `cloud` profile packaging and the unit and Arquillian tests (on the project's own H2 database with Payara Micro) were run. Test the image locally with compose before the first deployment.
