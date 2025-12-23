# Cloud-Native Web Application (Spring Boot API)

## Overview

This repository contains the **core backend application** of a cloud-native system, implemented as a **RESTful API using Spring Boot**.

The application is responsible for all **synchronous business logic**, including user and product management, image handling, health monitoring, and publishing events for asynchronous workflows. It is designed to run in a production AWS environment using immutable infrastructure and automated CI/CD pipelines.

This project is part of a **three-repository architecture**, where each repository has a clear and independent responsibility, similar to real-world industry systems.

---

## System Architecture & Repository Links

The complete system is intentionally split into three repositories:

| Repository                      | Purpose                                          |
| ------------------------------- | ------------------------------------------------ |
| **Web Application (this repo)** | Core Spring Boot REST API and business logic     |
| **Infrastructure Repository**   | AWS infrastructure provisioning using Terraform  |
| **Serverless Repository**       | Asynchronous email verification using AWS Lambda |

### Repository Links

* **Web Application (Spring Boot API)**
  [https://github.com/CSYE6225Assignments/webapp](https://github.com/CSYE6225Assignments/webapp)

* **Infrastructure (Terraform on AWS)**
  [https://github.com/CSYE6225Assignments/tf-aws-infra](https://github.com/CSYE6225Assignments/tf-aws-infra)

* **Serverless (Lambda Email Verification)**
  [https://github.com/CSYE6225Assignments/serverless](https://github.com/CSYE6225Assignments/serverless)
  
---

## Why This Repository Exists

In real-world cloud systems:

* Application code changes frequently
* Infrastructure must be stable, auditable, and reproducible
* Asynchronous workflows should scale independently

This repository exists to **isolate application logic** from infrastructure and serverless execution. It enables:

* Faster and safer application deployments
* Independent scaling of API, infrastructure, and async workflows
* Clear ownership boundaries
* Easier debugging and maintenance

This repository **does not provision infrastructure** and **does not send emails directly**.

---

## Core Responsibilities

### What This Repository Handles

* REST APIs for users, products, images, and health checks
* Authentication and authorization using HTTP Basic Auth
* Business validations and ownership checks
* Data persistence in MySQL
* Image storage abstraction (local filesystem or Amazon S3)
* Publishing user-verification events to Amazon SNS

### What This Repository Does Not Handle

* AWS resource creation (VPC, EC2, RDS, ALB, etc.)
* Terraform modules or state
* Lambda code or deployment
* DNS, certificates, or networking configuration

---

## Technology Stack

* Java 17
* Spring Boot (Web, Security, Data JPA)
* MySQL 8 (Docker locally, Amazon RDS in production)
* Amazon S3 (production image storage)
* GitHub Actions (CI)
* Packer (immutable AMI builds)

---

## Local Development

### Prerequisites

* Java 17+
* Docker and Docker Compose

### Start MySQL

```bash
docker compose up -d
```

### Run the Application

```bash
./mvnw spring-boot:run
```

The application runs at:

```
http://localhost:8080
```

---

## Application Flow (High Level)

1. Client sends an HTTP request to the API
2. Spring Security authenticates the request (if required)
3. Controller validates request structure
4. Service layer enforces business rules and ownership
5. Data is persisted or retrieved from MySQL
6. Optional side effects:

   * Images stored locally or in S3
   * Events published to SNS
7. API returns an HTTP response with appropriate status code

---

## API Overview

### Health Check

* `GET /healthz`
* Used by load balancers and auto scaling groups
* Performs a lightweight database check

Returns:

* `200 OK` if DB is reachable
* `503 Service Unavailable` otherwise

### Users

* Create user (public)
* Fetch and update user (authenticated, owner only)

### Products

* Create, read, update, delete products
* Public read access
* Mutations restricted to product owner

### Images

* Upload and delete restricted to product owner
* Public read access
* Stored locally in development and in S3 in production

---

## Authentication & Authorization

* HTTP Basic Authentication
* Passwords hashed using BCrypt
* Stateless design (no sessions)
* Ownership enforced on all mutating endpoints

---

## Image Storage Design

Image storage is abstracted behind a service layer.

### Development and Testing

* Uses local filesystem storage
* Enabled via configuration: `storage.type=local`
* Images stored using user/product partitioning

### Production

* Uses Amazon S3
* Enabled via configuration: `storage.type=s3`
* IAM role attached to EC2 instances (no hardcoded credentials)

This abstraction ensures the same application code runs unchanged across environments.

---

## Testing Strategy

* Integration tests run against a real MySQL instance
* Controllers, validation, security, and edge cases covered
* Image storage tests isolated using local filesystem

Run tests:

```bash
mvn clean test
```

---

## CI/CD Overview

### Continuous Integration

* Triggered on pull requests to `main`
* Runs full test suite
* Blocks merge on failure

### Artifact Generation

* Application packaged as a JAR using Maven
* JAR is used as input for AMI creation

---

## Immutable Infrastructure with Packer

This repository uses **Packer** to build immutable Amazon Machine Images (AMIs).

### Why Packer Is Used

* Avoids configuring servers at runtime
* Ensures identical environments across deployments
* Reduces deployment failures
* Improves security by minimizing installed software

### How the Packer Flow Works

1. CI builds the Spring Boot JAR
2. Packer launches a temporary EC2 instance
3. Provisioning scripts:

   * Install Java runtime
   * Create application user
   * Copy application JAR to `/opt/csye6225/`
   * Configure systemd service
4. Temporary instance is stopped
5. AMI is created and shared with target AWS account

The AMI contains:

* Java runtime
* Application JAR
* systemd service

The AMI does **not** contain:

* Database credentials
* Environment-specific configuration
* AWS secrets

All runtime configuration is injected later via EC2 user-data.

---

## Runtime Configuration (Production)

At instance launch, user-data scripts:

* Fetch secrets from AWS Secrets Manager
* Generate `application.properties`
* Start the Spring Boot service

This allows the same AMI to be reused across environments.

---

## Logging & Health Monitoring

* Application logs written to local files
* Logs streamed to Amazon CloudWatch
* `/healthz` endpoint used by ALB and ASG for health checks

---

## Summary

This repository implements a **production-grade Spring Boot backend service** with:

* Clean separation of concerns
* Real integration testing
* Immutable deployment model
* Clear boundaries with infrastructure and serverless layers

Together with the infrastructure and serverless repositories, it forms a **complete, scalable cloud-native system**.
