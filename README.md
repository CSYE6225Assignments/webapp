# HealthCheck API

Minimal `/healthz` endpoint that verifies DB connectivity by inserting a row into `health_checks`.  
Stores time in **UTC** (`Instant`). **GET only**. Requests with a body are **400**. Non-GET methods are **405** with `Allow: GET`.

---

## 1) Security (env files)

- **Never commit real secrets.** Use `.env` locally; keep it ignored by git.

`.env.example`
```
# MySQL (placeholders — replace locally)
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

## 2) MySQL with Docker (UTC pinned)

`docker-compose.yml` (key service)
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

## 3) Run the app

Using Maven wrapper:
```bash
./mvnw spring-boot:run
```

Key Spring config (already set in `application.properties`):
```properties
server.port=${APP_PORT:8080}
spring.datasource.url=jdbc:mysql://${DB_HOST:localhost}:${DB_PORT:3306}/${MYSQL_DATABASE:healthdb}?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
spring.datasource.username=${MYSQL_USER:user}
spring.datasource.password=${MYSQL_PASSWORD:pass123}
spring.jpa.hibernate.ddl-auto=update
spring.jpa.properties.hibernate.jdbc.time_zone=UTC
```

> If you later containerize the app too, use `DB_HOST=mysql-db` (service name) instead of `localhost`.

---

## 4) Endpoint & Edge-Case Behavior

**Paths supported**
- `GET /healthz`
- `GET /healthz/` (trailing slash also OK)

**Method rules**
- Only **GET** is allowed.
  - Any other method → **405 Method Not Allowed**  
    Includes header: `Allow: GET`

**Payload rules**
- If request has a body (`Content-Length` > 0) **or** `Transfer-Encoding` header → **400 Bad Request**

**Health action**
- On valid GET without payload, insert one row into `health_checks`:
  - Insert OK → **200 OK**
  - Insert fails (DB down/unreachable) → **503 Service Unavailable**

**Common response headers**
- `Cache-Control: no-cache, no-store, must-revalidate`
- `Pragma: no-cache`
- `X-Content-Type-Options: nosniff`

**UTC guarantees**
- Code: `Instant.now()` (UTC)
- JDBC/Hibernate: `hibernate.jdbc.time_zone=UTC`
- DB/container: `TZ=UTC` + `--default-time-zone='+00:00'`

---

## 5) Quick Tests (cURL)

Healthy (200):
```bash
curl -i http://localhost:8080/healthz
curl -i http://localhost:8080/healthz/
```

Bad Request due to payload (400):
```bash
curl -i -X GET http://localhost:8080/healthz -d "payload"
```

Method Not Allowed (405 + Allow: GET):
```bash
curl -i -X POST   http://localhost:8080/healthz
curl -i -X PUT    http://localhost:8080/healthz
curl -i -X DELETE http://localhost:8080/healthz
```

Unhealthy (503) example (stop DB then call):
```bash
docker compose stop mysql
curl -i http://localhost:8080/healthz
```

---

## 6) Troubleshooting

- **503**: DB not ready/wrong creds/host/port. Check `.env` and `docker compose logs -f mysql`.
- **400**: Remove request body from GET calls.
- **405**: Use `GET` only; other methods are intentionally blocked.

---

## 7) Project Layout (key)

```
src/main/java/com/example/healthcheckapi/
  controller/HealthController.java
  entity/HealthCheck.java
  repository/HealthCheckRepository.java
  service/HealthCheckService.java
src/main/resources/application.properties
docker-compose.yml
.env.example
README.md
```
