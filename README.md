# HealthCheck API

Spring Boot REST API with **User**, **Product**, and **HealthCheck** resources.  
Implements authentication (Basic Auth), input validation, and DB health verification.

---

## 1) Prerequisites

- **Java**: 17+
- **Maven**: Wrapper included (`./mvnw`)
- **Docker**: 20+ (with Docker Compose)
- **System**: 2+ GB RAM, 1+ CPU core, stable internet

---

## 2) Frameworks & Libraries

- **Spring Boot** (Web, Data JPA, Security, Validation)
- **Spring Security** with Basic Auth
- **MySQL 8** (via Docker)
- **Hibernate** (JPA provider)
- **BCrypt** (password hashing)
- **SLF4J** (logging)

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

👉 Inside Docker network, use `DB_HOST=mysql-db`.

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

### HealthCheck
```bash
curl -i http://localhost:8080/healthz
```

### User
```bash
# Create user
curl -i -X POST http://localhost:8080/v1/user \
  -H "Content-Type: application/json" \
  -d '{"username":"test@example.com","password":"password123","first_name":"John","last_name":"Doe"}'

# Fetch user
curl -i -u test@example.com:password123 http://localhost:8080/v1/user/1
```

### Product
```bash
# Create product
curl -i -X POST http://localhost:8080/v1/product \
  -u test@example.com:password123 \
  -H "Content-Type: application/json" \
  -d '{"name":"Widget","description":"A test widget","sku":"W123","manufacturer":"Acme","quantity":5}'
```

---

## 9) Troubleshooting

- **503**: DB not running or wrong creds → check `.env` and `docker compose logs -f mysql`
- **401/403**: Wrong/missing Basic Auth header
- **400**: Validation error (e.g., invalid email, password < 8 chars)
- **405**: Wrong HTTP method

---

## 10) Project Layout

```
src/main/java/com/example/healthcheckapi/
  config/SecurityConfig.java
  controller/ (UserController, ProductController, HealthController)
  entity/ (User.java, Product.java, HealthCheck.java)
  exception/GlobalExceptionHandler.java
  repository/ (UserRepository, ProductRepository, HealthCheckRepository)
  service/ (UserService, ProductService, HealthCheckService)
src/main/resources/application.properties
docker-compose.yml
.env.example
README.md
```
