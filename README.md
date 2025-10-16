# HealthCheck API

Spring Boot REST API with **User**, **Product**, and **HealthCheck** resources.  
Implements authentication (Basic Auth), input validation, DB health verification, comprehensive integration testing, and CI automation.

---

## 1) Prerequisites

- **Java**: 17+
- **Maven**: Wrapper included (`./mvnw`)
- **Docker**: 20+ (with Docker Compose)
- **System**: 2+ GB RAM, 1+ CPU core, stable internet

---

## 2) Frameworks & Libraries

### Core Dependencies
- **Spring Boot** (Web, Data JPA, Security, Validation)
- **Spring Security** with Basic Auth
- **MySQL 8** (via Docker)
- **Hibernate** (JPA provider)
- **BCrypt** (password hashing)
- **SLF4J** (logging)

### Testing Dependencies
- **Spring Boot Test**
- **MySQL** (real database for integration tests)
- **Spring Security Test**
- **MockMvc** (API testing)
- **HikariCP** (connection pool testing)

---

## 3) Environment Setup

Use `.env` for local secrets (never commit real values).

`.env.example`
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
```

---

## 4) Database (MySQL in Docker, UTC pinned)

`docker-compose.yml` (excerpt)
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

Start DB:
```bash
docker compose up -d
```

---

## 5) Run the App

Using Maven wrapper:
```bash
./mvnw spring-boot:run
```

Key Spring config (`application.properties`):
```properties
server.port=${APP_PORT:8080}
spring.datasource.url=jdbc:mysql://${DB_HOST:localhost}:${DB_PORT:3306}/${MYSQL_DATABASE:healthdb}?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
spring.datasource.username=${MYSQL_USER:user}
spring.datasource.password=${MYSQL_PASSWORD:pass123}
spring.jpa.hibernate.ddl-auto=update
spring.jpa.properties.hibernate.jdbc.time_zone=UTC
```

Inside Docker network, use `DB_HOST=mysql-db`.

---

## 6) API Overview

### HealthCheck
- **GET /healthz**  
  Inserts a row into `health_checks` (UTC).
  - Success → `200 OK`
  - DB unreachable → `503 Service Unavailable`
  - GET with body → `400 Bad Request`
  - Non-GET → `405 Method Not Allowed`

**Sample Response (200 OK):**
```json
(no body, status only)
```

---

### Users
- **POST /v1/user** → Create new user (no auth required)
- **GET /v1/user/{id}** → Fetch user (auth required)
- **PUT /v1/user/{id}** → Update user (auth required)

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

---

### Products
- **POST /v1/product** → Create product (auth required)
- **GET /v1/product/{id}** → Fetch product (public)
- **PUT /v1/product/{id}** → Update product (auth required, must be owner)
- **PATCH /v1/product/{id}** → Partial update (auth required, must be owner)
- **DELETE /v1/product/{id}** → Delete product (auth required, must be owner)

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

---

## 7) Security

- **Basic Auth** with username (email) + password
- Passwords hashed with **BCrypt**
- Stateless: no sessions, no cookies
- Endpoint rules (from `SecurityConfig`):
  - `/healthz` → public
  - `POST /v1/user` → public
  - `GET /v1/product/*` → public
  - Other `/v1/**` → requires auth

---

## 8) Testing

### Integration Tests

All tests are **true integration tests** using real MySQL database instances - no mocking.

Run all tests:
```bash
mvn clean test
```

Run specific test class:
```bash
mvn test -Dtest=HealthControllerIntegrationTest
```

#### Test Coverage
- **HealthCheck Tests**: All HTTP methods, headers, payload validation, DB connectivity, 503 scenarios
- **User Tests**: CRUD operations, authentication, validation, duplicate prevention, empty/null field handling
- **Product Tests**: CRUD with ownership, SKU uniqueness, quantity boundaries, concurrent operations
- **Edge Cases**: Special characters, boundary values, concurrent requests, data persistence
- **Error Scenarios**: Database unavailability (503), authentication failures (401/403), validation errors (400)

#### Test Configuration
- Tests use **real MySQL database** via Docker for realistic integration testing
- Database cleanup between tests using `TestDatabaseCleanup` listener
- Configuration in `src/test/resources/application-test.properties`
- Specialized test for 503 scenarios using unavailable database connection

### Manual Testing Examples

#### HealthCheck
```bash
curl -i http://localhost:8080/healthz
```

#### User
```bash
# Create user
curl -i -X POST http://localhost:8080/v1/user \
  -H "Content-Type: application/json" \
  -d '{"username":"test@example.com","password":"password123","first_name":"John","last_name":"Doe"}'

# Fetch user
curl -i -u test@example.com:password123 http://localhost:8080/v1/user/1
```

#### Product
```bash
# Create product
curl -i -X POST http://localhost:8080/v1/product \
  -u test@example.com:password123 \
  -H "Content-Type: application/json" \
  -d '{"name":"Widget","description":"A test widget","sku":"W123","manufacturer":"Acme","quantity":5}'
```

---

## 9) CI with GitHub Actions

### Automated Testing
All pull requests to `main` branch trigger automated tests via GitHub Actions.

**Workflow Configuration** (`.github/workflows/ci.yml`):
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
The `main` branch is protected with:
- Require pull request before merging
- Require status checks to pass (CI Tests)
- Require branches to be up-to-date
- No force pushes allowed
- No branch deletions allowed

---

## 10) Troubleshooting

- **503**: DB not running or wrong creds → check `.env` and `docker compose logs -f mysql`
- **401/403**: Wrong/missing Basic Auth header
- **400**: Validation error (e.g., invalid email, password < 8 chars, empty required fields)
- **405**: Wrong HTTP method
- **Test Failures**: Check test logs with `mvn test -X`
- **CI Failures**: Check GitHub Actions tab for detailed logs
- **Integration Test Issues**: Ensure MySQL Docker container is running for local tests

---

## 11) Project Structure

```
src/
├── main/
│   ├── java/com/example/healthcheckapi/
│   │   ├── config/SecurityConfig.java
│   │   ├── controller/
│   │   │   ├── HealthController.java
│   │   │   ├── UserController.java
│   │   │   └── ProductController.java
│   │   ├── entity/
│   │   │   ├── User.java
│   │   │   ├── Product.java
│   │   │   └── HealthCheck.java
│   │   ├── exception/GlobalExceptionHandler.java
│   │   ├── repository/
│   │   └── service/
│   └── resources/
│       └── application.properties
├── test/
│   ├── java/com/example/healthcheckapi/
│   │   ├── config/
│   │   │   └── TestDatabaseCleanup.java
│   │   └── integration/
│   │       ├── BaseIntegrationTest.java
│   │       ├── HealthControllerIntegrationTest.java
│   │       ├── HealthController503Test.java
│   │       ├── UserControllerIntegrationTest.java
│   │       └── ProductControllerIntegrationTest.java
│   └── resources/
│       └── application-test.properties
.github/
└── workflows/
    └── ci.yml
docker-compose.yml
.env.example
pom.xml
README.md
```

---

## 12) Development Workflow
1. **Fork** the repository
2. **Create feature branch**: `git checkout -b feature-name`
3. **Make changes** and write tests
4. **Run tests locally**: `mvn clean test` (ensure MySQL Docker is running)
5. **Commit changes**: `git commit -m "Add feature"`
6. **Push to fork**: `git push origin feature-name`
7. **Create Pull Request** to `main`
8. **Wait for CI checks** to pass
9. **Merge** after approval

---

## 13) Custom AMI with Packer

This project uses **Packer** to create custom Amazon Machine Images (AMIs) with the application and all dependencies pre-installed.

### Prerequisites
- **Packer**: 1.8+ installed locally
- **AWS CLI**: Configured with `dev` and `demo` profiles
- **AWS Accounts**: DEV and DEMO accounts set up with proper IAM permissions

### AMI Configuration

The custom AMI includes:
- **Ubuntu 24.04 LTS** base image
- **OpenJDK 17 JRE** (headless)
- **MariaDB 10.11** running locally
- **Application JAR** pre-installed
- **Systemd service** configured to auto-start
- **csye6225 user** (no login shell)
- **UTC timezone** configuration

### Directory Structure
```
packer/
├── aws-ubuntu.pkr.hcl       # Main Packer template
├── variables.pkr.hcl        # Variable definitions
├── dev.pkrvars.hcl          # Dev environment config (gitignored)
└── scripts/
    ├── setup-system.sh      # Install Java and system packages
    ├── setup-database.sh    # Install and configure MariaDB
    ├── setup-application.sh # Deploy application
    ├── setup-service.sh     # Configure systemd service
    └── cleanup.sh           # Reduce AMI size
```

### Building AMI Locally
```bash
# 1. Build the application JAR
./mvnw clean package -DskipTests

# 2. Navigate to packer directory
cd packer

# 3. Get your AWS infrastructure IDs
aws ec2 describe-subnets --profile dev --query 'Subnets[0].SubnetId' --output text
aws ec2 describe-security-groups --filters "Name=group-name,Values=packer-build-sg" --profile dev

# 4. Update dev.pkrvars.hcl with your values
nano dev.pkrvars.hcl

# 5. Initialize Packer
packer init .

# 6. Format Packer files
packer fmt .

# 7. Validate template
packer validate -var-file="dev.pkrvars.hcl" .

# 8. Build AMI (takes 5-10 minutes)
packer build -var-file="dev.pkrvars.hcl" .
```

### AMI Features

**Security:**
- Private AMI (not public)
- Shared only with DEMO account
- No hardcoded credentials
- csye6225 user runs with `/usr/sbin/nologin` shell

**Application Setup:**
- JAR installed at `/opt/csye6225/application.jar`
- Configuration at `/opt/csye6225/application.properties`
- Owned by `csye6225:csye6225`
- Systemd service auto-starts on boot

**Database:**
- MariaDB runs locally (not exposed externally)
- Configured for UTC timezone
- Strict SQL mode enabled
- UTF-8mb4 character set

**What's NOT Included:**
- Git (not installed)
- Build tools (Maven removed after build)
- Development dependencies

### CI/CD Automation

#### Workflow 1: Packer Validation (on Pull Requests)
**File:** `.github/workflows/packer-validate.yml`

Runs on every PR to `main`:
- Checks Packer formatting (`packer fmt -check`)
- Validates template syntax (`packer validate`)
- Uses dummy JAR for validation (no build needed)
- Comments results on PR
- Blocks merge if checks fail

**Triggers:**
```yaml
on:
  pull_request:
    branches: [ main ]
```

#### Workflow 2: AMI Build (on Merge to Main)
**File:** `.github/workflows/packer-build.yml`

Runs when PR is merged to organization's `main`:
1. **Integration Tests** - Runs all tests with MySQL
2. **Build JAR** - Creates application artifact on GitHub runner
3. **Build AMI** - Creates custom AMI with Packer
4. **Verify Sharing** - Confirms AMI is shared with DEMO account

**Triggers:**
```yaml
on:
  push:
    branches: [ main ]
# Only runs in organization repo (not forks)
if: github.repository_owner == 'CSYE6225Assignments'
```

### GitHub Secrets & Variables Required

**Secrets** (Settings → Secrets and variables → Actions → Secrets):
- `AWS_ACCESS_KEY_ID` - GitHub Actions IAM user access key
- `AWS_SECRET_ACCESS_KEY` - GitHub Actions IAM user secret key
- `DEMO_ACCOUNT_ID` - AWS DEMO account ID
- `MYSQL_PASSWORD` - Production database password
- `TEST_MYSQL_ROOT_PASSWORD` - Test database root password
- `TEST_MYSQL_PASSWORD` - Test database user password

**Variables** (Settings → Secrets and variables → Actions → Variables):
- `AWS_REGION` - AWS region (us-east-1)
- `SUBNET_ID` - Subnet for Packer builds
- `SECURITY_GROUP_ID` - Security group for Packer builds
- `MYSQL_DATABASE` - Production database name
- `MYSQL_USER` - Production database username
- `TEST_MYSQL_DATABASE` - Test database name
- `TEST_MYSQL_USER` - Test database username

### Verifying AMI
```bash
# List your AMIs
aws ec2 describe-images --owners self --profile dev

# Check if shared with DEMO
aws ec2 describe-image-attribute \
  --image-id ami-XXXXX \
  --attribute launchPermission \
  --profile dev

# Launch test instance from AMI
aws ec2 run-instances \
  --image-id ami-XXXXX \
  --instance-type t2.micro \
  --key-name csye6225-aws-key \
  --security-group-ids sg-XXXXX \
  --subnet-id subnet-XXXXX \
  --profile dev
```

### Testing Deployed Instance

After launching an EC2 instance from the AMI:
```bash
# Wait 60-90 seconds for app to auto-start
sleep 90

# Test health endpoint (replace with your instance IP)
curl http://INSTANCE_IP:8080/healthz

# SSH and verify (optional)
ssh -i ~/.ssh/csye6225-aws-key ubuntu@INSTANCE_IP
sudo systemctl status csye6225
sudo systemctl status mariadb
which git  # Should return nothing
```

### Troubleshooting Packer

**Build fails during validation:**
- Check AWS credentials: `aws sts get-caller-identity --profile dev`
- Verify subnet and security group IDs in `dev.pkrvars.hcl`
- Ensure JAR exists: `ls -lh ../target/*.jar`

**SSH timeout during build:**
- Security group must allow port 22
- Subnet must be in supported AZ (not us-east-1e for t3.small)

**AMI not shared with DEMO:**
- Check `ami_users` variable includes DEMO account ID
- Verify in workflow logs: "Verify AMI is shared with DEMO account"

**Application doesn't start on instance:**
- Check systemd service: `sudo systemctl status csye6225`
- Check logs: `sudo journalctl -u csye6225 -n 100`
- Verify MariaDB is running: `sudo systemctl status mariadb`

---