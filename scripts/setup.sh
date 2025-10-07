#!/bin/bash

# CSYE6225 Direct Setup Script
# This extracts webapp.zip directly to /opt/csye6225 and runs everything from there

set -e

if [ "$EUID" -ne 0 ]; then
    echo "Please run as root"
    exit 1
fi

export DEBIAN_FRONTEND=noninteractive

echo "Starting setup..."

# Load .env if exists
if [ -f ".env" ]; then
    export $(cat .env | grep -v '^#' | xargs)
else
    MYSQL_DATABASE="healthdb"
    MYSQL_USER="user"
    MYSQL_PASSWORD="pass123"
    TEST_DB="testdb"
    TEST_USER="testuser"
    TEST_PASSWORD="testpass"
fi

# System setup
apt update -y
apt upgrade -y
apt install -y unzip mariadb-server openjdk-17-jdk maven

# Start MariaDB
systemctl start mariadb
systemctl enable mariadb

# Create databases
mysql -e "CREATE DATABASE IF NOT EXISTS ${MYSQL_DATABASE};"
mysql -e "CREATE USER IF NOT EXISTS '${MYSQL_USER}'@'localhost' IDENTIFIED BY '${MYSQL_PASSWORD}';"
mysql -e "GRANT ALL PRIVILEGES ON ${MYSQL_DATABASE}.* TO '${MYSQL_USER}'@'localhost';"
mysql -e "CREATE DATABASE IF NOT EXISTS ${TEST_DB};"
mysql -e "CREATE USER IF NOT EXISTS '${TEST_USER}'@'localhost' IDENTIFIED BY '${TEST_PASSWORD}';"
mysql -e "GRANT ALL PRIVILEGES ON ${TEST_DB}.* TO '${TEST_USER}'@'localhost';"
mysql -e "FLUSH PRIVILEGES;"

# Create user/group
groupadd -f csye6225
useradd -r -g csye6225 -s /usr/sbin/nologin csye6225_user || true

# Extract webapp.zip DIRECTLY to /opt/csye6225
echo "Extracting webapp.zip to /opt/csye6225..."
mkdir -p /opt/csye6225
unzip -o webapp.zip -d /opt/csye6225/

# Copy .env to app directory
[ -f ".env" ] && cp .env /opt/csye6225/

# Set permissions
chown -R csye6225_user:csye6225 /opt/csye6225
find /opt/csye6225 -type d -exec chmod 755 {} \;
find /opt/csye6225 -type f -exec chmod 644 {} \;
[ -f /opt/csye6225/mvnw ] && chmod +x /opt/csye6225/mvnw

# NOW CD TO /opt/csye6225 AND RUN EVERYTHING FROM THERE
cd /opt/csye6225

# Build from /opt/csye6225
echo "Building application in /opt/csye6225..."
if [ -f "mvnw" ]; then
    ./mvnw clean package -DskipTests
else
    mvn clean package -DskipTests
fi

# Find JAR (we're now in /opt/csye6225)
JAR_FILE=$(find target -name "*.jar" -type f ! -name "*sources.jar" | head -1)

# Create systemd service
cat > /etc/systemd/system/csye6225.service <<EOF
[Unit]
Description=CSYE6225 Application
After=mariadb.service

[Service]
User=csye6225_user
Group=csye6225
WorkingDirectory=/opt/csye6225
ExecStart=/usr/bin/java -jar /opt/csye6225/${JAR_FILE}
Restart=always

[Install]
WantedBy=multi-user.target
EOF

# Start application
systemctl daemon-reload
systemctl enable csye6225
systemctl start csye6225

echo "Setup complete!"
echo "Application running at: http://$(hostname -I | awk '{print $1}'):8080"
echo "Test: curl http://localhost:8080/healthz"