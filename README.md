# HealthCheck API — Spring Boot, MySQL, S3, Packer AMI, CI/CD

A production-ready REST API built with **Spring Boot**. It supports user and product management, image uploads (local in dev, **AWS S3** in prod), robust **integration tests** on a real MySQL instance, and an **immutable infrastructure** path using **Packer** to bake custom AMIs. CI pipelines run on GitHub Actions.

---

## Table of Contents
- [1) Prerequisites](#1-prerequisites)
- [2) Frameworks & Libraries](#2-frameworks--libraries)
- [3) Environment Setup](#3-environment-setup)
- [4) Database (MySQL in Docker, UTC pinned)](#4-database-mysql-in-docker-utc-pinned)
- [5) Run the App](#5-run-the-app)
- [6) API Overview](#6-api-overview)
- [7) Security](#7-security)
- [8) Testing](#8-testing)
- [9) Image Upload API Details](#9-image-upload-api-details)
- [10) CI with GitHub Actions](#10-ci-with-github-actions)
- [11) Custom AMI with Packer](#11-custom-ami-with-packer)
- [12) Storage and Cleanup](#12-storage-and-cleanup)
- [13) Troubleshooting](#13-troubleshooting)
- [14) Project Structure](#14-project-structure)
- [15) Development Workflow](#15-development-workflow)
- [16) API Specifications](#16-api-specifications)
- [17) Key Features](#17-key-features)

---

## 1) Prerequisites

- **Java:** 17+
- **Maven:** Wrapper included (`./mvnw`)
- **Docker:** 20+ (with Docker Compose)
- **System:** 2+ GB RAM, 1+ CPU core, stable internet
- **AWS CLI (for production deployment):** configured with appropriate profile

---

## 2) Frameworks & Libraries

### Core Dependencies
- Spring Boot (Web, Data JPA, Security, Validation)
- Spring Security with **Basic Auth**
- MySQL 8 (via **Docker** for local, **RDS** for production)
- Hibernate (JPA provider)
- BCrypt (password hashing)
- SLF4J (logging)
- AWS SDK for Java 2.x (**S3** integration for image storage)

### Testing Dependencies
- Spring Boot Test
- **MySQL** (real database for integration tests)
- Spring Security Test
- **MockMvc** (API testing)
- **Mockito** (S3Service unit tests)
- **HikariCP** (connection pool testing)

---

## 3) Environment Setup

Use a **`.env`** for local secrets (**never commit real values**).

**`.env.example`**
```env
# MySQL (replace locally)
MYSQL_ROOT_PASSWORD=replace_me
MYSQL_DATABASE=healthdb
MYSQL_USER=appuser
MYSQL_PASSWORD=replace_me

# App/DB connectivity
DB_HOST=localhost
DB_PORT=3306
APP_PORT=8080

# AWS Configuration (for production deployment)
AWS_REGION=us-east-1
S3_BUCKET_NAME=
STORAGE_TYPE=local  # Use 'local' for dev, 's3' for production
```

**Environment Variables for AWS Deployment:**
- `DB_HOST` – RDS endpoint hostname
- `DB_PORT` – Database port (3306)
- `MYSQL_DATABASE` – Database name
- `MYSQL_USER` – Database username
- `MYSQL_PASSWORD` – Database password
- `S3_BUCKET_NAME` – S3 bucket name for images
- `AWS_REGION` – AWS region
- `STORAGE_TYPE` – `s3` for production, `local` for development/tests

> **Note:** Inside Docker network, use `DB_HOST=mysql-db`.

---

## 4) Database (MySQL in Docker, UTC pinned)

**`docker-compose.yml` (excerpt):**
```yaml
version: '3.8'
services:
  mysql:
    image: mysql:8.0
    container_name: mysql-db
    environment:
      MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD}
      MYSQL_DATABASE: ${MYSQL_DATABASE}
      MYSQL_USER: ${MYSQL_USER}
      MYSQL_PASSWORD: ${MYSQL_PASSWORD}
      TZ: UTC
    ports:
      - "${DB_PORT}:3306"
    volumes:
      - mysql-data:/var/lib/mysql
    command: --default-time-zone='+00:00'

volumes:
  mysql-data:
```

**Start DB:**
```bash
docker compose up -d
```

---

## 5) Run the App

### Local Development (with Local Storage)

Using Maven wrapper:
```bash
./mvnw spring-boot:run
```

**Key Spring config (`src/main/resources/application.properties`):**
```properties
server.port=${APP_PORT:8080}

# Database Configuration
spring.datasource.url=jdbc:mysql://${DB_HOST:localhost}:${DB_PORT:3306}/${MYSQL_DATABASE:healthdb}?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
spring.datasource.username=${MYSQL_USER:user}
spring.datasource.password=${MYSQL_PASSWORD:pass123}
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver

# Connection Pool Settings
spring.datasource.hikari.maximum-pool-size=10
spring.datasource.hikari.minimum-idle=0
spring.datasource.hikari.connection-timeout=3000
spring.datasource.hikari.initialization-fail-timeout=0

# JPA Configuration
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=false
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQL8Dialect
spring.jpa.properties.hibernate.jdbc.time_zone=UTC

# AWS Configuration (for production)
aws.region=${AWS_REGION:us-east-1}
aws.s3.bucket-name=${S3_BUCKET_NAME:}
storage.type=${STORAGE_TYPE:s3}

# File Upload Configuration
spring.servlet.multipart.enabled=true
spring.servlet.multipart.max-file-size=5MB
spring.servlet.multipart.max-request-size=5MB
file.upload-dir=./uploads
```

---

## 6) API Overview

### HealthCheck

- `GET /healthz`
  - Inserts a row into `health_checks` (UTC).
  - **Success → 200 OK**
  - **DB unreachable → 503 Service Unavailable**
  - **GET with body → 400 Bad Request**
  - **Non-GET → 405 Method Not Allowed**

**Sample Response (200 OK):**
```json
(no body, status only)
```

### Users

- `POST /v1/user` → Create new user (**no auth required**)
- `GET /v1/user/{id}` → Fetch user (**auth required**)
- `PUT /v1/user/{id}` → Update user (**auth required**)

**Request (POST /v1/user):**
```json
{
  "username": "test@example.com",
  "password": "password123",
  "first_name": "John",
  "last_name": "Doe"
}
```

**Response (201 Created):**
```json
{
  "id": 1,
  "username": "test@example.com",
  "first_name": "John",
  "last_name": "Doe",
  "account_created": "2025-09-25T14:33:45Z",
  "account_updated": "2025-09-25T14:33:45Z"
}
```

### Products

- `POST /v1/product` → Create product (**auth required**)
- `GET /v1/product/{id}` → Fetch product (**public**)
- `PUT /v1/product/{id}` → Update product (**auth required, must be owner**)
- `PATCH /v1/product/{id}` → Partial update (**auth required, must be owner**)
- `DELETE /v1/product/{id}` → Delete product (**auth required, must be owner**)

**Request (POST /v1/product):**
```json
{
  "name": "Widget",
  "description": "A test widget",
  "sku": "W123",
  "manufacturer": "Acme",
  "quantity": 5
}
```

**Response (201 Created):**
```json
{
  "id": 1,
  "name": "Widget",
  "description": "A test widget",
  "sku": "W123",
  "manufacturer": "Acme",
  "quantity": 5,
  "date_added": "2025-09-25T14:35:00Z",
  "date_last_updated": "2025-09-25T14:35:00Z",
  "owner_user_id": 1
}
```

### Images

Upload, retrieve, and delete images associated with products. Images are stored in **AWS S3** in production or **local filesystem** during development.

- `POST /v1/product/{product_id}/image` → Upload image (**auth required, must be product owner**)
- `GET /v1/product/{product_id}/image` → Get all images (**public**)
- `GET /v1/product/{product_id}/image/{image_id}` → Get specific image details (**public**)
- `DELETE /v1/product/{product_id}/image/{image_id}` → Delete image (**auth required, must be product owner**)

**Supported File Types:** `jpg`, `jpeg`, `png` (case-insensitive)

**Request (POST /v1/product/1/image):**
```bash
curl -X POST http://localhost:8080/v1/product/1/image \
  -u test@example.com:password123 \
  -F "file=@image.jpg"
```

**Response (201 Created):**
```json
{
  "image_id": 1,
  "product_id": 1,
  "file_name": "image.jpg",
  "date_created": "2025-01-22T10:40:00.000Z",
  "s3_bucket_path": "user_1/product_1/550e8400-e29b-41d4-a716-446655440000.jpg"
}
```

**Response (GET /v1/product/1/image):**
```json
[
  {
    "image_id": 1,
    "product_id": 1,
    "file_name": "image.jpg",
    "date_created": "2025-01-22T10:40:00.000Z",
    "s3_bucket_path": "user_1/product_1/550e8400-e29b-41d4-a716-446655440000.jpg"
  }
]
```

**Storage Partitioning:**
```
user_{userId}/product_{productId}/{uuid}.{ext}
```

Examples:
```
user_1/product_2/550e8400-e29b-41d4-a716-446655440000.jpg
```

**Security & Ownership:**
- Only product owners can upload/delete images
- Image retrieval is public (no auth required)
- Users cannot delete images for other users’ products
- Images cannot be updated (only create/delete)

**Delete Behavior:**
- Hard delete removes image from both database and storage (S3/filesystem)
- Deleting a product cascades delete of associated images

**Error Handling:**
- **400** – Invalid file type (not jpg/jpeg/png), empty/missing file
- **401** – Missing or invalid auth
- **403** – User doesn’t own the product
- **404** – Product or image not found
- **500** – S3/upload/delete/storage I/O errors

---

## 7) Security

- **Basic Auth** with username (email) + password
- Passwords hashed with **BCrypt**
- **Stateless:** no sessions, no cookies

**Endpoint rules (from SecurityConfig):**
```
/healthz → public
POST /v1/user → public
GET /v1/product/* → public
GET /v1/product/*/image → public
GET /v1/product/*/image/* → public
Other /v1/** → requires auth
```

---

## 8) Testing

**Integration Tests** — all tests use **real MySQL** instances (no mocking).

Run all tests:
```bash
mvn clean test
```

Run specific tests:
```bash
mvn test -Dtest=ImageControllerIntegrationTest
mvn test -Dtest=S3ServiceTest
```

### Test Coverage

**HealthCheck Tests:**
- All HTTP methods, headers, payload validation, DB connectivity, 503 scenarios

**User Tests:**
- CRUD operations, authentication, validation, duplicate prevention, empty/null field handling

**Product Tests:**
- CRUD with ownership, SKU uniqueness, quantity boundaries, concurrent ops

**Image Tests (49 tests):**
- Upload validation (jpg, jpeg, png, case-insensitive)
- Invalid file types (gif, pdf, txt) → 400
- Authentication and authorization → 401/403
- Ownership validation
- Multiple images with same filename (UUID uniqueness)
- File system storage and cleanup
- Cascade deletion when product deleted
- Storage path partitioning (user_X/product_Y)
- Data integrity after upload/delete
- `WWW-Authenticate` header verification

**S3Service Tests (8 tests):**
- Unit tests with mocked S3Client
- Upload/delete operations
- Exception handling
- UUID generation and uniqueness
- Correct partitioning logic

**Edge Cases:** Special characters, boundary values, concurrent requests, data persistence  
**Error Scenarios:** DB unavailability (503), authentication failures (401/403), validation errors (400)

**Total Test Count:** 100+ tests

### Test Configuration

- Tests use real MySQL database via Docker for realistic integration testing
- Image tests use local filesystem storage (not S3) for isolation
- Database cleanup via **TestDatabaseCleanup** listener
- Test config in `src/test/resources/application-test.properties`
- S3Service uses Mockito
- Specialized test for 503 scenarios using unavailable DB connection

**`src/test/resources/application-test.properties`:**
```properties
# application-test.properties
storage.type=local  # Use local storage for tests, not S3
file.upload-dir=./build/test-uploads
```

### Manual Testing Examples

**HealthCheck**
```bash
curl -i http://localhost:8080/healthz
```

**User**
```bash
# Create user
curl -i -X POST http://localhost:8080/v1/user \
  -H "Content-Type: application/json" \
  -d '{"username":"test@example.com","password":"password123","first_name":"John","last_name":"Doe"}'

# Fetch user
curl -i -u test@example.com:password123 http://localhost:8080/v1/user/1

# Update user
curl -i -X PUT http://localhost:8080/v1/user/1 \
  -u test@example.com:password123 \
  -H "Content-Type: application/json" \
  -d '{"first_name":"Johnny","last_name":"Doe"}'
```

**Product**
```bash
# Create product
curl -i -X POST http://localhost:8080/v1/product \
  -u test@example.com:password123 \
  -H "Content-Type: application/json" \
  -d '{"name":"Widget","description":"A test widget","sku":"W123","manufacturer":"Acme","quantity":5}'

# Get product (public - no auth)
curl -i http://localhost:8080/v1/product/1

# Update product
curl -i -X PUT http://localhost:8080/v1/product/1 \
  -u test@example.com:password123 \
  -H "Content-Type: application/json" \
  -d '{"name":"Super Widget","description":"Updated","sku":"W123","manufacturer":"Acme","quantity":10}'

# Partial update (PATCH)
curl -i -X PATCH http://localhost:8080/v1/product/1 \
  -u test@example.com:password123 \
  -H "Content-Type: application/json" \
  -d '{"quantity":15}'

# Delete product
curl -i -X DELETE http://localhost:8080/v1/product/1 \
  -u test@example.com:password123
```

**Image Upload/Management**
```bash
# Create a product first
curl -i -X POST http://localhost:8080/v1/product \
  -u test@example.com:password123 \
  -H "Content-Type: application/json" \
  -d '{"name":"Laptop","description":"Test","sku":"LAP-001","manufacturer":"TechCorp","quantity":10}'

# Upload an image
curl -i -X POST http://localhost:8080/v1/product/1/image \
  -u test@example.com:password123 \
  -F "file=@/path/to/image.jpg"

# Get all images (no auth required)
curl -i http://localhost:8080/v1/product/1/image

# Get specific image details (no auth required)
curl -i http://localhost:8080/v1/product/1/image/1

# Delete an image (auth required, must be owner)
curl -i -X DELETE http://localhost:8080/v1/product/1/image/1 \
  -u test@example.com:password123
```

---

## 9) Image Upload API Details

### File Validation
- **Allowed Extensions:** `jpg`, `jpeg`, `png` (case-insensitive)
- **Max File Size:** **5MB** per file
- **Max Request Size:** **5MB**
- **Validation:** Server-side extension and size checks

### Storage Strategy

**Development/Testing:**
- Local filesystem storage (`./uploads` or `./build/test-uploads`)
- Controlled by `storage.type=local` property
- Same partitioning structure as S3

**Production (AWS):**
- S3 storage with IAM role authentication
- Controlled by `storage.type=s3` property
- Bucket name passed via environment variable
- No hardcoded AWS credentials

**Storage Partitioning:**
```
storage-root/
├── user_1/
│   ├── product_1/
│   │   ├── 550e8400-e29b-41d4-a716-446655440000.jpg
│   │   └── 7c9e6679-7425-40de-944b-e07fc1f90ae7.png
│   └── product_2/
│       └── 123e4567-e89b-12d3-a456-426614174000.jpg
└── user_2/
    └── product_1/
        └── 9f4e2f3c-5b8a-4d5e-9c3f-8a7b6c5d4e3f.jpg
```

**Security & Ownership**
- Users can only upload/delete images on **their own** products
- Image retrieval is **public**
- Images are **immutable** (only create/delete)
- **UUID-based** filenames prevent collisions

### Storage Implementation

**`S3Service.java`**
- AWS SDK v2 with **DefaultCredentialsProvider**
- Uses **IAM role** on EC2 (prod)
- Falls back to local AWS credentials in dev (if needed)
- Generates UUIDs
- Partitions by `user_{id}/product_{id}`

**`LocalStorageService.java`**
- Used in dev & tests
- Same partition structure
- `file.upload-dir` configurable

**`ImageService.java`**
- Abstracts storage impl
- Switches between S3/local via `storage.type`
- Validates extension/size
- Manages DB metadata

### AWS SDK Credential Resolution
Order used by DefaultCredentialsProvider:
1. **Environment Variables** (`AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`)
2. **System Properties** (`aws.accessKeyId`, `aws.secretAccessKey`)
3. **AWS Credentials File** (`~/.aws/credentials`)
4. **EC2 Instance Profile** (**IAM role**, production)

> **Production:** EC2 instance profile is used automatically.  
> **Development:** Set `storage.type=local` to skip S3.

---

## 10) CI with GitHub Actions

### Automated Testing
All pull requests to `main` trigger automated tests via **GitHub Actions**.

**`.github/workflows/ci.yml`:**
```yaml
name: CI Tests

on:
  pull_request:
    branches: [ main ]

jobs:
  test:
    runs-on: ubuntu-latest
    
    services:
      mysql:
        image: mysql:8.0
        env:
          MYSQL_ROOT_PASSWORD: root
          MYSQL_DATABASE: testdb
          MYSQL_USER: testuser
          MYSQL_PASSWORD: testpass
        ports:
          - 3306:3306
        options: >-
          --health-cmd="mysqladmin ping --silent"
          --health-interval=10s
          --health-timeout=5s
          --health-retries=5

    steps:
      - uses: actions/checkout@v3
      - name: Set up JDK 17
        uses: actions/setup-java@v3
        with:
          java-version: '17'
          distribution: 'temurin'
      - name: Wait for MySQL
        run: |
          for i in {1..30}; do
            if mysqladmin ping -h"127.0.0.1" -P"3306" --silent; then
              echo "MySQL is ready!"
              break
            fi
            echo "Waiting for MySQL... ($i/30)"
            sleep 2
          done
      - name: Run tests
        env:
          SPRING_DATASOURCE_URL: jdbc:mysql://localhost:3306/testdb?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
          SPRING_DATASOURCE_USERNAME: testuser
          SPRING_DATASOURCE_PASSWORD: testpass
        run: mvn clean test
```

### Branch Protection
- Require pull request before merging
- Require status checks to pass (**CI Tests**)
- Require branches to be up-to-date
- No force pushes allowed
- No branch deletions allowed

---

## 11) Custom AMI with Packer

This project uses **Packer** to create custom **Amazon Machine Images (AMIs)** with the application and dependencies pre-installed.

### Prerequisites
- **Packer:** 1.8+ installed locally
- **AWS CLI:** Configured with `dev` and `demo` profiles
- **AWS Accounts:** DEV and DEMO with permissions
- **Application JAR:** built with Maven

### AMI Configuration
Includes:
- **Ubuntu 24.04 LTS** base
- **OpenJDK 17 JRE (headless)**
- App JAR at `/opt/csye6225/application.jar`
- **systemd** service to auto-start app
- `csye6225` user (no login shell)
- **UTC** timezone
- **No local DB** (connects to RDS)
- **No git/build tools** (minimal footprint)

### Directory Structure
```
packer/
├── packer.pkr.hcl           # Main Packer template
├── variables.pkr.hcl        # Variable definitions
├── dev.pkrvars.hcl          # Dev environment config (gitignored)
└── scripts/
    ├── setup-system.sh      # Install Java and system packages
    ├── setup-application.sh # Deploy application JAR
    ├── setup-service.sh     # Configure systemd service
    └── cleanup.sh           # Reduce AMI size
```

### Building AMI Locally
```bash
# 1. Build the application JAR
./mvnw clean package -DskipTests

# 2. Navigate to packer directory
cd packer

# 3. Create dev.pkrvars.hcl with your AWS configuration
cat > dev.pkrvars.hcl <<EOF
aws_region         = "us-east-1"
aws_profile        = "dev"
subnet_id          = "subnet-xxxxx"  # Your default VPC subnet
security_group_id  = "sg-xxxxx"      # Security group allowing SSH
ami_users          = ["123456789012"]  # DEMO account ID
app_artifact_path  = "../target/health-check-api-0.0.1-SNAPSHOT.jar"
EOF

# 4. Initialize Packer
packer init .

# 5. Format Packer files
packer fmt .

# 6. Validate template
packer validate -var-file="dev.pkrvars.hcl" .

# 7. Build AMI
packer build -var-file="dev.pkrvars.hcl" .
```

**Example Build Output:**
```
==> Builds finished. The artifacts of successful builds are:
--> amazon-ebs.ubuntu: AMIs were created:
us-east-1: ami-0123456789abcdef0
```

### AMI Features

**Security:**
- Private AMI (not public)
- Shared only with DEMO account
- No hardcoded credentials
- `csye6225` user runs with `/usr/sbin/nologin`
- Minimal packages (reduced attack surface)

**Application Setup:**
- JAR at `/opt/csye6225/application.jar`
- Config provided at **runtime via user-data** (not baked in)
- Owned by `csye6225:csye6225`
- **systemd service:** `/etc/systemd/system/csye6225.service`

**Systemd Service Configuration:**
```ini
[Unit]
Description=CSYE6225 Spring Boot Application
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=csye6225
Group=csye6225
WorkingDirectory=/opt/csye6225
ExecStart=/usr/bin/java -jar /opt/csye6225/application.jar --spring.config.location=file:/opt/csye6225/application.properties
Restart=on-failure
RestartSec=10
StandardOutput=journal
StandardError=journal
SyslogIdentifier=csye6225

# Security settings
NoNewPrivileges=true
PrivateTmp=true

[Install]
WantedBy=multi-user.target
```

**Not Included in AMI:**
- Git, Maven/build tools, local DB
- Any application configuration

### CI/CD Automation

**Workflow 1: Packer Validation (PRs)** — `.github/workflows/packer-validate.yml`
- `packer fmt -check`
- `packer validate`
- Uses dummy JAR for validation
- Comments on PR & blocks merge if failing

**Workflow 2: AMI Build (on merge to `main`)** — `.github/workflows/packer-build.yml`
- Run integration tests with MySQL
- Build JAR on GitHub runner
- Build AMI with Packer
- Verify sharing with DEMO account

**Triggers (examples):**
```yaml
on:
  pull_request:
    branches: [ main ]

# AMI build
on:
  push:
    branches: [ main ]
# Only runs in organization repo (not forks)
if: github.repository_owner == 'CSYE6225Assignments'
```

**GitHub Secrets & Variables Required**

*Secrets*
- `AWS_ACCESS_KEY_ID`
- `AWS_SECRET_ACCESS_KEY`
- `DEMO_ACCOUNT_ID`

*Variables*
- `AWS_REGION` (e.g., `us-east-1`)
- `SUBNET_ID`
- `SECURITY_GROUP_ID`

### Verifying AMI
```bash
# List your AMIs
aws ec2 describe-images --owners self --profile dev

# Check if shared with DEMO account
aws ec2 describe-image-attribute \
  --image-id ami-XXXXX \
  --attribute launchPermission \
  --profile dev

# Get AMI details
aws ec2 describe-images \
  --image-ids ami-XXXXX \
  --profile dev \
  --query 'Images[0].[ImageId,Name,CreationDate,State]' \
  --output table
```

### Testing Deployed Instance
```bash
# Wait 60-90 seconds for app to auto-start
sleep 90

# Test health endpoint (replace with your instance IP)
curl http://INSTANCE_IP:8080/healthz

# SSH and verify (optional)
ssh -i ~/.ssh/csye6225-aws-key.pem ubuntu@INSTANCE_IP

# Check service status
sudo systemctl status csye6225

# Check application logs
sudo journalctl -u csye6225 -f

# Verify JAR exists
ls -la /opt/csye6225/

# Check if application.properties was created by user-data
cat /opt/csye6225/application.properties

# Verify no Git installed
which git  # Should return nothing
```

### Troubleshooting Packer
- **Build fails during validation:** check AWS creds/profile, subnet/SG IDs, and that the JAR exists.
- **SSH timeout during build:** ensure SG allows port 22, supported AZ for instance type, review Packer logs.
- **AMI not shared with DEMO:** ensure `ami_users` includes DEMO ID and IAM permissions allow sharing.
- **App doesn’t start on instance:** inspect `systemctl status`, `journalctl -u csye6225`, and `/var/log/user-data.log`.

---

## 12) Storage and Cleanup

### Local Development

**Upload Directory Structure:**
```
./uploads/
└── user_{userId}/
    └── product_{productId}/
        ├── {uuid-1}.jpg
        └── {uuid-2}.png
```

**Cleanup:**
```bash
# Remove all uploaded files
rm -rf ./uploads

# Remove test uploads
rm -rf ./build/test-uploads

# Stop and remove Docker containers and volumes
docker compose down -v
```

### AWS Production
- Images stored in S3 with **automatic encryption** and **lifecycle management**.
- See infra repo for bucket configuration details.

### .gitignore
```gitignore
uploads/
test-uploads/
build/test-uploads/
target/
*.jar
.env
dev.pkrvars.hcl
demo.pkrvars.hcl
packer/manifest.json
```

---

## 13) Troubleshooting

### Application Issues
- **503**: DB not running or wrong creds → check `.env` and `docker compose logs -f mysql`
- **401/403**: Wrong/missing Basic Auth header
- **400**: Validation error (invalid email, password < 8 chars, empty fields, invalid image type)
- **405**: Wrong HTTP method
- **500 (Image upload)**: S3 bucket not configured or IAM role missing (prod)

### Test Issues
- **Test Failures**: `mvn test -X`
- **CI Failures**: check GitHub Actions logs
- **Integration Tests**: ensure MySQL Docker is running
- **Image Tests**: verify `storage.type=local` in `application-test.properties`

### Packer Issues
- **Build Timeout**: SG must allow SSH (22)
- **JAR Not Found**: build application first with `mvn clean package`
- **AWS Auth Fails**: verify profile with `aws sts get-caller-identity --profile dev`
- **AMI Not Shared**: check `ami_users` and IAM permissions

### Production Deployment Issues
- **S3 Upload Fails**: verify `aws.s3.bucket-name` and IAM role on EC2
- **Database Connection**: validate RDS endpoint/SG/creds
- **Application Won’t Start**: inspect `/var/log/user-data.log` and `journalctl -u csye6225`

---

## 14) Project Structure
```
src/
├── main/
│   ├── java/com/example/healthcheckapi/
│   │   ├── config/
│   │   │   └── SecurityConfig.java
│   │   ├── controller/
│   │   │   ├── HealthController.java
│   │   │   ├── UserController.java
│   │   │   ├── ProductController.java
│   │   │   └── ImageController.java
│   │   ├── entity/
│   │   │   ├── User.java
│   │   │   ├── Product.java
│   │   │   ├── HealthCheck.java
│   │   │   └── Image.java
│   │   ├── exception/
│   │   │   └── GlobalExceptionHandler.java
│   │   ├── repository/
│   │   │   ├── UserRepository.java
│   │   │   ├── ProductRepository.java
│   │   │   ├── HealthCheckRepository.java
│   │   │   └── ImageRepository.java
│   │   └── service/
│   │       ├── UserService.java
│   │       ├── ProductService.java
│   │       ├── HealthCheckService.java
│   │       ├── ImageService.java
│   │       ├── S3Service.java
│   │       └── LocalStorageService.java
│   └── resources/
│       └── application.properties
├── test/
│   ├── java/com/example/healthcheckapi/
│   │   ├── config/
│   │   │   └── TestDatabaseCleanup.java
│   │   ├── service/
│   │   │   └── S3ServiceTest.java
│   │   └── integration/
│   │       ├── BaseIntegrationTest.java
│   │       ├── HealthControllerIntegrationTest.java
│   │       ├── HealthController503Test.java
│   │       ├── UserControllerIntegrationTest.java
│   │       ├── ProductControllerIntegrationTest.java
│   │       └── ImageControllerIntegrationTest.java
│   └── resources/
│       └── application-test.properties
.github/
└── workflows/
    ├── ci.yml
    ├── packer-validate.yml
    └── packer-build.yml
packer/
├── packer.pkr.hcl
├── variables.pkr.hcl
├── dev.pkrvars.hcl (gitignored)
└── scripts/
    ├── setup-system.sh
    ├── setup-application.sh
    ├── setup-service.sh
    └── cleanup.sh
docker-compose.yml
.env.example
.gitignore
pom.xml
README.md
```

---

## 15) Development Workflow

1. **Fork** the repository
2. Create feature branch: `git checkout -b feature-name`
3. Make changes and **write tests**
4. Run tests locally: `mvn clean test` (ensure MySQL Docker is running)
5. Commit: `git commit -m "Add feature"`
6. Push to fork: `git push origin feature-name`
7. **Open PR** to `main`
8. Wait for CI to pass (integration tests + Packer validation)
9. Merge after approval — AMI builds automatically on merge to org’s `main`

---

## 16) API Specifications

Full API documentation available at:  
**Swagger:** https://app.swaggerhub.com/apis-docs/csye6225-webapp/cloud-native-webapp/fall2025-a6

### API Compliance
 All request/response payloads in JSON  
 No UI implemented (REST API only)  
 Proper HTTP status codes (200, 201, 204, 400, 401, 403, 404, 500, 503)  
 Authentication & authorization via Basic Auth  
 Image upload with S3 storage  
 Image metadata stored in database  
 Hard delete removes from both database and storage  
 Ownership validation for all mutations  
 User/product partitioning in storage

---

## 17) Key Features

### Database
- **UTC Timezone** enforcement across timestamps
- Connection pooling with **HikariCP**
- Auto-schema updates via Hibernate DDL
- **MySQL 8** dialect with `utf8mb4`
- Health checks verify DB connectivity

### Security
- **BCrypt** password hashing (cost 10)
- Stateless auth (no sessions)
- Read-only fields ignored in requests (`id`, timestamps, `owner_user_id`)
- Input validation with **Jakarta Validation**
- Global exception handling with consistent error responses
- IAM role–based S3 access (no credential exposure)

### Image Management
- Multipart upload support
- File type validation (jpg, jpeg, png only)
- Size limits (5MB)
- UUID-based naming prevents collisions
- User/product partitioning ensures isolation
- Cascade deletion when products are deleted
- Storage abstraction (S3 in prod, local in dev/test)

### Data Integrity
- Cascade delete for related entities
- Unique constraints on usernames and SKUs
- Referential integrity with foreign keys
- Optimistic locking via JPA timestamps
- DB indexes on foreign keys for performance

---

