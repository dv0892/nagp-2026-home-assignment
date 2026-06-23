# Project Understanding and Documentation

## Quick Reference Commands

### MongoDB Operations

#### Test MongoDB Connectivity
```powershell
kubectl exec -n nagp2026-assignment -it mongodb-0 -- mongosh -u adminUser -p securePassword123 --eval "db.adminCommand('ping')"
```

#### Connect to MongoDB from a Client Pod
```powershell
kubectl run mongo-test-client -n nagp2026-assignment --rm -it --image=mongo:6.0 -- bash
mongosh "mongodb://adminUser:securePassword12345@mongodb-0.mongodb-headless.nagp2026-assignment.svc.cluster.local:27017/?authSource=admin"
```

#### Connect to MongoDB from Outside the Cluster
```powershell
# Terminal 1: Set up port forwarding
kubectl port-forward pod/mongodb-0 27017:27017 -n nagp2026-assignment

# Terminal 2: Connect using MongoDB client on local machine
# Connection string: mongodb://adminUser:securePassword12345@localhost:27017/?authSource=admin
```

### Application Testing

#### Test HPA Load Generation
```powershell
kubectl run load-generator --rm -i --tty --image=busybox:1.36 --restart=Never -- /bin/sh -c "while true; do wget -q -O- http://8.233.80.16/api/v1/assignments --header 'Host: app.nagp2026-assignment.com'; done"
```

---

## Requirement Understanding

### Functional Requirements

The application is built to expose REST endpoints for managing academic assignment records with the following capabilities:

#### API Endpoints

- **POST /assignments**
  - Accepts a JSON request body with assignment details
  - Validates input using Jakarta Validation constraints (NotBlank, Min/Max on score)
  - Persists assignment record to MongoDB
  - Returns the created Assignment object with auto-generated MongoDB ObjectId and submission timestamp
  - Implementation: `src/main/java/org/nagp2026/web/AssignmentController.java::submitAssignment`

- **GET /assignments**
  - Lists all assignment records or filters by optional query parameters
  - Query parameters (mutually exclusive filtering):
    - `?studentName=<name>` — retrieves all assignments for a specific student
    - `?topicName=<topic>` — retrieves all assignments for a specific topic
    - `?minScore=<score>` — retrieves assignments with score >= minimum threshold
    - No parameters — returns all assignments in database
  - Implementation: `src/main/java/org/nagp2026/web/AssignmentController.java::fetchAssignments`

#### Data Model

An `Assignment` record stored in MongoDB `assignments` collection with the following fields:
- `id` — MongoDB ObjectId (auto-generated on creation)
- `student_name` — name of the student submitting the assignment
- `topic` — subject or topic of the assignment
- `score` — numeric score (0–100), validated at API layer
- `submission_time` — timestamp of submission (defaults to current time if not provided)

Implementation: `src/main/java/org/nagp2026/model/Assignment.java`

#### Input Validation

Request DTO enforces the following constraints:
- `studentName` — required, non-blank
- `topic` — required, non-blank
- `score` — required, numeric, between 0 and 100 (inclusive)

Implementation: `src/main/java/org/nagp2026/dto/AssignmentRequestDTO.java`

### Non-Functional Requirements

#### Deployment & Infrastructure

- Deploy the Spring Boot application in a Kubernetes cluster
- Use MongoDB StatefulSet with persistent storage for data durability
- Enable horizontal pod autoscaling for the application based on CPU utilization
- Configure health checks (liveness and readiness probes) for pod lifecycle management
- Use rolling updates for zero-downtime deployments
- Expose the application via Kubernetes Ingress resource for external access

#### Security & Configuration

- Store database credentials in Kubernetes Secrets (separate from application code)
- Use ConfigMap for non-sensitive configuration (database host, database name)
- Inject credentials and configuration via environment variables

#### Resource Management

- Define resource requests and limits to ensure predictable scheduling and prevent resource starvation
- Implement horizontal pod autoscaler (HPA) to scale replicas based on CPU metrics

---

## Assumptions

### Kubernetes Environment

- A Kubernetes cluster is available with:
  - An ingress controller installed and configured (e.g., nginx-ingress or cloud provider ingress)
  - The host `app.nagp2026-assignment.com` is configured to route to the cluster
  - Metrics server or equivalent metrics provider installed for HPA to read CPU utilization
  - A storage provisioner compatible with the configured StorageClass

### Storage & Provisioning

- Storage provisioner for `gcp-standard-storage` StorageClass is available in the cluster
  - **Note:** This assumes Google Kubernetes Engine (GKE) environment with GCP PD CSI driver
  - For other cloud providers or on-premises clusters, the StorageClass must be adapted to match available provisioners

### Container Image Registry

- The container image `dv1992/app-nagp2026-assignment-k8s:4.0` is accessible from the cluster
  - May be pulled from Docker Hub (if public) or requires registry credentials configured in the cluster

- The MongoDB image `mongo:6.0` is accessible (standard official image)

### Application Runtime

- Application built on Java 17 or later (use of Java records and Jakarta APIs indicates modern Java version)
- Spring Boot runtime will pick up environment variables for database connectivity:
  - `MONGODB_HOST` — hostname of MongoDB service
  - `MONGODB_DATABASE` — name of the database
  - `MONGODB_USERNAME` — root username for MongoDB
  - `MONGODB_PASSWORD` — root password for MongoDB

### Security & Secrets

- Secret values in manifests are placeholder examples and must be rotated for production environments
- Kubernetes secret encryption at rest or external secret management (e.g., HashiCorp Vault, AWS Secrets Manager) should be configured in production

### Database Connectivity

- MongoDB StatefulSet will be deployed in the same namespace (`nagp2026-assignment`)
- Headless service `mongodb-headless` is used to provide stable network identity for single MongoDB replica
- DNS resolution for `mongodb-0.mongodb-headless.nagp2026-assignment.svc.cluster.local` is available within the cluster

---

## Solution Overview

### Architecture

The solution consists of three main layers:

#### 1. Application Layer (Spring Boot)

**Components:**

- **Controller** (`AssignmentController.java`)
  - REST endpoints for HTTP requests
  - Request validation via `@Valid` annotation
  - Delegates business logic to service layer

- **Service** (`AssignmentService.java`)
  - Business logic and orchestration
  - Data transformation between DTOs and domain models
  - Implements CRUD operations and filtering logic

- **Repository** (`AssignmentRepository.java`)
  - Extends `MongoRepository` for MongoDB data access
  - Provides derived query methods (`findByStudentName`, `findByTopic`)
  - Custom query method `findHighScorers` for score-based filtering

- **DTO** (`AssignmentRequestDTO.java`)
  - Request payload structure with validation constraints
  - Prevents invalid data from entering the system

- **Domain Model** (`Assignment.java`)
  - Java record representing the MongoDB document
  - Default submission time assigned in compact constructor if not provided
  - Mapped to `assignments` MongoDB collection

#### 2. Data Persistence Layer (MongoDB)

- **StatefulSet** — ensures stable identity and persistent storage
- **PersistentVolumeClaim** — 10Gi storage allocation for MongoDB data
- **Headless Service** — provides stable DNS names for pod discovery
- **Secret** — stores database credentials securely

#### 3. Kubernetes Infrastructure

**Networking:**
- **Service (ClusterIP)** — exposes application on port 8081 internally, mapped to container port 8080
- **Ingress** — routes external traffic from host `app.nagp2026-assignment.com` to the service

**Workload Management:**
- **Deployment** — manages application replica pods with RollingUpdate strategy
- **HorizontalPodAutoscaler** — automatically scales replicas between 2 and 10 based on CPU utilization

**Configuration:**
- **ConfigMap** — stores non-sensitive configuration (database host, database name)
- **Secret** — stores sensitive credentials (database username, password)

**Health & Reliability:**
- **Liveness Probe** — restarts unhealthy pods
- **Readiness Probe** — prevents routing traffic to not-yet-ready pods

### Data Flow

```
Client Request
    ↓
Ingress (app.nagp2026-assignment.com)
    ↓
Service (ClusterIP, port 8081)
    ↓
Deployment Pod (Spring Boot app, port 8080)
    ↓
AssignmentController (REST endpoint handler)
    ↓
AssignmentService (business logic)
    ↓
AssignmentRepository (MongoRepository)
    ↓
MongoDB StatefulSet (persistence via PVC)
```

### API Specifications

#### Create Assignment

```http
POST /assignments HTTP/1.1
Content-Type: application/json

{
  "studentName": "Alice Johnson",
  "topic": "Kubernetes Orchestration",
  "score": 92
}
```

**Response (201 Created):**
```json
{
  "id": "507f1f77bcf86cd799439011",
  "studentName": "Alice Johnson",
  "topic": "Kubernetes Orchestration",
  "score": 92,
  "submissionTime": "2026-06-23T14:30:00"
}
```

#### List All Assignments

```http
GET /assignments HTTP/1.1
```

#### Filter by Student

```http
GET /assignments?studentName=Alice%20Johnson HTTP/1.1
```

#### Filter by Topic

```http
GET /assignments?topicName=Kubernetes%20Orchestration HTTP/1.1
```

#### Filter by Minimum Score

```http
GET /assignments?minScore=80 HTTP/1.1
```

---

## Justification for the Resources Utilized

### Kubernetes Object Selection

#### Why StatefulSet for MongoDB?

StatefulSet is the correct choice for stateful workloads like databases because:

- **Stable Network Identity**: Each pod gets a stable hostname (e.g., `mongodb-0`) that persists across restarts
- **Stable Storage**: PersistentVolumeClaim is uniquely associated with each pod replica, preventing data loss
- **Ordered Deployment**: Pods are created/deleted in order, important for distributed database deployments
- **Headless Service**: Internal DNS resolves to individual pod IPs, allowing direct pod-to-pod communication required for databases

Alternative (Deployment) would not provide these guarantees and risks data loss or connection inconsistencies.

#### Why Headless Service for MongoDB?

- Headless service (`clusterIP: None`) bypasses the load balancer and provides direct DNS resolution to pod IPs
- Enables MongoDB driver to connect directly to specific replicas (important for replica sets)
- Even for single-replica deployments, headless service maintains the same architecture pattern for future scalability

#### Why Deployment for Application?

- Deployment is appropriate for stateless workloads like the Spring Boot application
- Provides replica management, rolling updates, and self-healing (auto-restart on failure)
- Integrates seamlessly with HPA for dynamic scaling

### Resource Requests and Limits

#### CPU: requests="200m", limits="400m"

**Rationale:**

- **Requests (200m)**: Reserves 0.2 CPU cores per pod for the Kubernetes scheduler
  - Ensures the pod has guaranteed CPU time for baseline JVM operations
  - Low value suitable for testing/small production deployments
  - Avoids pod starvation from cluster overprovisioning

- **Limits (400m)**: Hard ceiling of 0.4 CPU cores per pod
  - Prevents runaway JVM processes from consuming all cluster CPU
  - Reduces noisy neighbor problem in multi-tenant clusters
  - Value is conservative; adjust based on load testing results

**Tuning Guidance:**
- Increase requests if cold startup is slow (JVM class loading overhead)
- Increase limits if 50th percentile latency increases under sustained load
- Profile with actual workloads before finalizing

#### Memory: requests="300Mi", limits="512Mi"

**Rationale:**

- **Requests (300Mi)**: Reserves 300 MiB for pod scheduling
  - Covers minimal heap size for Spring Boot application
  - Accounts for JVM overhead, framework initialization, and small working set
  
- **Limits (512Mi)**: Hard ceiling of 512 MiB
  - Prevents out-of-memory kills from impacting cluster stability
  - JVM OutOfMemory protection is enforced via Linux CGroup limits

**Tuning Guidance:**
- Set JVM `-Xms` (initial heap) close to requests value to avoid GC pauses on startup
- Set JVM `-Xmx` (max heap) to approximately limits value with headroom for non-heap usage
- Monitor actual memory usage under peak load and adjust accordingly
- Example JVM args: `-Xms256m -Xmx450m` to leave 62 MiB for off-heap overhead

### Storage Configuration

#### PersistentVolume Claim: 10Gi

**Rationale:**

- 10Gi is the minimum allocation for GKE PD standard tier
- Suitable for small-to-medium datasets in development and testing
- Includes operational overhead for:
  - Database operational journal
  - Temporary files and indices
  - Backup snapshots (if implemented)

**Growth Planning:**
- Monitor MongoDB `du` (disk usage) regularly
- Set alerts for usage >70% of allocated storage
- Plan expansion if growth rate trends upward
- Consider implementing automated backup retention policies

#### StorageClass: gcp-standard-storage

**Rationale:**

- Configured for Google Kubernetes Engine (GKE) with Google Cloud Platform Persistent Disks
- `pd.csi.storage.gke.io` provisioner ensures compatibility with GCP infrastructure
- `allowVolumeExpansion: true` permits dynamic volume resizing without downtime

**For Other Environments:**
- *AWS EKS*: Use `ebs.csi.aws.com` provisioner with `gp3` type
- *Azure AKS*: Use `disk.csi.azure.com` provisioner with `managed-premium` or `standard` class
- *On-premises*: Use `hostpath`, `local`, or third-party provisioners (e.g., Longhorn, Ceph)

### Horizontal Pod Autoscaler Configuration

#### Scale Range: minReplicas=2, maxReplicas=10

**Rationale:**

- **minReplicas=2**: Maintains high availability
  - Allows at least one pod to be replaced during updates without service interruption
  - Provides redundancy against node failures
  - Prevents single point of failure in production
  
- **maxReplicas=10**: Controls cost and prevents runaway scaling
  - Limits maximum resource consumption
  - Prevents cascade failures from unlimited scale-up
  - Can be increased if load testing justifies higher peak capacity

#### CPU Utilization Target: 50%

**Rationale:**

- 50% is a conservative target that maintains headroom for:
  - Garbage collection pauses (not fully tracked by CPU metrics)
  - Request latency spikes
  - Smooth scaling without oscillation
  - JVM warm-up and optimization

- Trade-off: slightly higher cost vs. improved user experience and stability

**Sensitivity:**
- Higher targets (70–80%) reduce costs but increase risk of response time degradation
- Lower targets (30–40%) increase stability but increase infrastructure costs
- Recommended tuning: Load test and monitor tail latencies (p95, p99) to choose optimal target

### Probes: Liveness & Readiness

#### Path: `/api/v1/actuator/health`

- Spring Boot Actuator endpoint provides real-time application health status
- Checks both application readiness and critical dependencies

#### Configuration: initialDelaySeconds=60

**Rationale:**

- 60 seconds accounts for JVM startup time:
  - JVM process startup: ~5–10 seconds
  - Spring context initialization: ~30–45 seconds
  - Database connection establishment: ~5–10 seconds
  - Total: typically 40–60 seconds in cold start

- Shorter delays risk pod kill before startup completes, causing restart loops

**Adjustments:**
- Production observations may show faster startup; reduce delay to 30–40 seconds if appropriate
- If using GraalVM native image, can reduce to 10–15 seconds
- Monitor pod transition logs for premature restarts due to short delays

#### Probe Frequency: periodSeconds=10

- 10-second probing interval balances:
  - Fast fault detection (detect failure within 30 seconds = 3 failed probes)
  - Minimal overhead from repeated health checks

#### Failure Threshold: failureThreshold=3

- Allows 3 consecutive failures before action taken:
  - Readiness: pod removed from Service endpoints (no new traffic)
  - Liveness: pod restarted by kubelet
  - Tolerance: ~30 seconds of failure before action, reducing flapping

### Deployment Strategy: RollingUpdate

#### Configuration: maxUnavailable=1, maxSurge=1

**Rationale:**

```
At any point during update:
- At most 1 pod is unavailable (removed from service)
- At most 1 extra pod is created (for replacement)
- Service maintains 3–4 available pods during update (with 4 replicas)
```

**Safety Properties:**
- Zero-downtime updates (requests continue being served)
- Reduced risk of cascading failures
- Conservative resource usage (doesn't spin up all new pods simultaneously)

**Trade-offs:**
- Updates take longer (replace pods sequentially)
- If faster updates required, increase both values: `maxUnavailable=2, maxSurge=2`

### ConfigMap & Secrets

#### ConfigMap for Non-Sensitive Data

```yaml
data:
  mongodb-host: "mongodb-0.mongodb-headless.nagp2026-assignment.svc.cluster.local"
  mongodb-database: "nagp2026-home-assignment"
```

**Rationale:**
- Database hostname and name are infrastructure details, not secrets
- ConfigMap allows changing these without rebuilding container image
- Enables environment parity (dev, staging, prod use same image, different ConfigMap)

#### Secret for Sensitive Credentials

```yaml
stringData:
  mongo-root-username: "adminUser"
  mongo-root-password: "securePassword12345"
```

**Rationale:**
- Credentials automatically base64-encoded by Kubernetes
- Separated from code and ConfigMap
- Can be encrypted at rest in production

**Production Hardening:**
- Replace with external secret manager (HashiCorp Vault, AWS Secrets Manager, Google Secret Manager)
- Enable Kubernetes secret encryption at rest
- Implement secret rotation policies
- Use least-privilege database users (not root)
- Audit secret access via RBAC and logging

---

## Deployment & Testing

### Prerequisites

- Kubernetes cluster (1.24+) with:
  - Ingress controller installed
  - Metrics server for HPA support
  - StorageClass matching cluster environment
  - kubectl CLI configured to access cluster

### Deployment Commands

#### 1. Create Namespace (if not already exists)

```powershell
kubectl create namespace nagp2026-assignment
```

or apply manifest:

```powershell
kubectl apply -f k8s-namespace.yaml
```

#### 2. Deploy MongoDB Stack

```powershell
kubectl apply -f k8s-mongodb.yaml
```

**Verify MongoDB is running:**

```powershell
# Check StatefulSet
kubectl get statefulset -n nagp2026-assignment

# Check PVC is bound
kubectl get pvc -n nagp2026-assignment

# Test MongoDB connectivity
kubectl exec -n nagp2026-assignment -it mongodb-0 -- mongosh -u adminUser -p securePassword123 --eval "db.adminCommand('ping')"
```

#### 3. Deploy Application

```powershell
kubectl apply -f k8s-app.yaml
```

**Verify application deployment:**

```powershell
# Check Deployment status
kubectl get deployment -n nagp2026-assignment

# Check pods are running
kubectl get pods -n nagp2026-assignment

# View pod logs
kubectl logs deployment/app-nagp2026-assignment-k8s -n nagp2026-assignment --tail=50
```

#### 4. (Optional) Enable Horizontal Pod Autoscaler

```powershell
kubectl apply -f k8s-hpa.yaml
```

**Verify HPA:**

```powershell
# Check HPA status
kubectl get hpa -n nagp2026-assignment

# Detailed HPA status
kubectl describe hpa hpa-app-nagp2026-assignment-k8s -n nagp2026-assignment
```

### Testing APIs Locally

#### Port-Forward Service

```powershell
# Forward service port 8081 to local 8081
kubectl port-forward svc/svc-app-spring-boot-k8s 8081:8081 -n nagp2026-assignment
```

In another terminal:

#### Create Assignment

```powershell
$body = @{
    studentName = "Alice Johnson"
    topic = "Kubernetes Orchestration"
    score = 92
} | ConvertTo-Json

Invoke-WebRequest -Uri "http://localhost:8081/assignments" `
  -Method POST `
  -ContentType "application/json" `
  -Body $body `
  | Select-Object -ExpandProperty Content | ConvertFrom-Json
```

#### List All Assignments

```powershell
Invoke-WebRequest -Uri "http://localhost:8081/assignments" `
  -Method GET | Select-Object -ExpandProperty Content | ConvertFrom-Json | ConvertTo-Json -Depth 2
```

#### Filter by Student Name

```powershell
Invoke-WebRequest -Uri "http://localhost:8081/assignments?studentName=Alice%20Johnson" `
  -Method GET | Select-Object -ExpandProperty Content | ConvertFrom-Json | ConvertTo-Json -Depth 2
```

#### Filter by Minimum Score

```powershell
Invoke-WebRequest -Uri "http://localhost:8081/assignments?minScore=80" `
  -Method GET | Select-Object -ExpandProperty Content | ConvertFrom-Json | ConvertTo-Json -Depth 2
```

### Testing HPA Behavior

Generate load to trigger autoscaling:

```powershell
# Run load generator in Kubernetes
kubectl run load-generator --rm -i --tty `
  --image=busybox:1.36 `
  --restart=Never `
  -n nagp2026-assignment `
  -- /bin/sh -c 'while true; do wget -q -O- http://svc-app-spring-boot-k8s:8081/assignments; done'
```

In another terminal, monitor HPA and pods:

```powershell
# Watch HPA metrics update
kubectl get hpa hpa-app-nagp2026-assignment-k8s -n nagp2026-assignment --watch

# Watch pods scale up
kubectl get pods -n nagp2026-assignment --watch
```

After a few minutes, replica count should increase as CPU utilization rises above 50% target; it will decrease when load stops.

---

## Recommendations for Production

### Security Enhancements

1. **Secrets Management**
   - Migrate from Kubernetes Secrets to external secret managers
   - Enable Kubernetes secret encryption at rest
   - Implement automated secret rotation every 90 days
   - Use least-privileged database users (not root)

2. **Network Security**
   - Enable NetworkPolicies to restrict traffic:
     - App pods can only reach MongoDB pods
     - Database pods only accept from app pods
   - Use Ingress TLS termination with valid certificates
   - Implement ingress authentication (OAuth2, OIDC)

3. **RBAC & Access Control**
   - Create service accounts with minimal required permissions
   - Audit all API access via audit logging
   - Implement Pod Security Policies or Pod Security Standards

### Observability & Monitoring

1. **Logging**
   - Aggregate application logs to centralized logging system (ELK, EFK, Loki)
   - Configure structured JSON logging from Spring Boot
   - Retain logs for at least 30 days for compliance

2. **Metrics & Monitoring**
   - Export JVM metrics via Micrometer + Prometheus endpoint
   - Set up Prometheus scrape config for the application
   - Create Grafana dashboards for:
     - Request rate, latency (p50, p95, p99)
     - JVM heap usage, GC frequency
     - MongoDB query latency and document counts
     - HPA metrics and scaling decisions

3. **Alerting**
   - Alert on pod restart loops
   - Alert on sustained high CPU or memory usage
   - Alert on MongoDB disk usage >70%
   - Alert on application error rate >1%
   - Alert on end-to-end request latency p99 > SLA threshold

### Database Optimization

1. **Indexing**
   - Create MongoDB indexes on frequently queried fields:
     ```
     db.assignments.createIndex({ "student_name": 1 })
     db.assignments.createIndex({ "topic": 1 })
     db.assignments.createIndex({ "score": 1 })
     ```
   - Monitor index usage and remove unused indexes

2. **Backup & Disaster Recovery**
   - Implement daily MongoDB backups (snapshots or mongodump)
   - Store backups in geographically distributed locations
   - Test restore procedures regularly
   - Document RTO/RPO requirements and validate compliance

3. **Performance Tuning**
   - Monitor MongoDB slow query logs
   - Optimize queries identified as slow
   - Consider READ/WRITE preference configurations for replica sets

### Application Hardening

1. **JVM Configuration**
   - Set `-Xms` close to requests value to reduce startup GC pause
   - Set `-Xmx` near limits to prevent OOM kills
   - Configure GC logs for diagnostics: `-Xlog:gc*:file=gc.log`
   - Profile memory usage under peak load

2. **Readiness Gating**
   - Extend readiness probe to verify database connectivity
   - Prevent traffic routing until all initialization complete
   - Consider graceful shutdown to drain connections

3. **Rate Limiting & Circuit Breaker**
   - Implement API rate limiting per client
   - Add circuit breaker for MongoDB connection pool
   - Gracefully degrade on database unavailability

### High Availability & Disaster Recovery

1. **Multi-Replica MongoDB**
   - Consider MongoDB ReplicaSet for HA (scale StatefulSet replicas to 3)
   - Enables automatic failover and read scaling

2. **Multi-Zone Deployment**
   - Deploy pods across multiple zones/nodes
   - Configure Pod Disruption Budgets (PDB) to maintain minimum replicas during maintenance
   - Ensure node affinity rules spread load

3. **Infrastructure as Code**
   - Use Helm or Kustomize to manage Kubernetes manifests
   - Version control all configuration changes
   - Implement GitOps for infrastructure deployments

### Cost Optimization

1. **Resource Right-Sizing**
   - Baseline load testing to determine minimum viable requests/limits
   - Reduce over-provisioned resources without sacrificing availability
   - Monitor actual vs. requested resource usage regularly

2. **Auto-Scaling Tuning**
   - Monitor HPA scaling decisions and adjust CPU target if under/over-scaling occurs
   - Consider predictive scaling during known peak times

3. **Storage Optimization**
   - Implement MongoDB compression for index/data
   - Set TTL on audit logs and temporary collections
   - Monitor disk usage growth and plan expansions

---

## Reference Files

- **Application Source**: `src/main/java/org/nagp2026/`
- **Kubernetes Manifests**: `k8s-*.yaml`
- **Build Configuration**: `build.gradle`
- **Docker**: `Dockerfile`

---

## Document Metadata

- **Author**: NAGP 2026 Assignment Team
- **Date Created**: June 23, 2026
- **Last Updated**: June 23, 2026
- **Kubernetes Version**: 1.24+
- **Spring Boot Version**: 3.x (inferred from Jakarta APIs)
- **Java Version**: 17+
- **Database**: MongoDB 6.0

