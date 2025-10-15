#!/bin/bash
set -e

echo "=== Starting Application Setup ==="

# Create user and group
sudo groupadd -f csye6225
sudo useradd -r -g csye6225 -s /usr/sbin/nologin csye6225 || true

# Create application directory
sudo mkdir -p /opt/csye6225

# Move application JAR
sudo mv /tmp/application.jar /opt/csye6225/application.jar

# Create application.properties
sudo tee /opt/csye6225/application.properties > /dev/null <<APPEOF
# Server Configuration
server.port=8080

# Database Configuration
spring.datasource.url=jdbc:mysql://localhost:3306/${MYSQL_DATABASE}?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
spring.datasource.username=${MYSQL_USER}
spring.datasource.password=${MYSQL_PASSWORD}
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver

# Connection Pool Settings
spring.datasource.hikari.maximum-pool-size=10
spring.datasource.hikari.minimum-idle=2
spring.datasource.hikari.connection-timeout=30000

# JPA Configuration
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=false
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQL8Dialect
spring.jpa.properties.hibernate.jdbc.time_zone=UTC
APPEOF

# Set ownership
sudo chown -R csye6225:csye6225 /opt/csye6225

# Set permissions
sudo chmod 750 /opt/csye6225
sudo chmod 640 /opt/csye6225/application.properties
sudo chmod 640 /opt/csye6225/application.jar

echo "=== Application Setup Complete ==="
