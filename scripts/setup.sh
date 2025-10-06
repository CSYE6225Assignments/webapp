#!/bin/bash

# CSYE6225 Application Setup Script
# This script sets up the application environment on Ubuntu 24.04 LTS

# Exit on any error
set -e

# Check if running as root
if [ "$EUID" -ne 0 ]; then
    echo "Please run this script with sudo or as root"
    exit 1
fi

# Avoid apt prompts (safer on Ubuntu 24.04)
export DEBIAN_FRONTEND=noninteractive

# Allow password to be set via environment variable (with default)
DB_PASS="${DB_PASS:-ItachiUchiha#22&}"

echo "Starting application setup..."

# 1. Update Package Lists
echo "Step 1: Updating package lists..."
apt update -y

# 2. Upgrade System Packages
echo "Step 2: Upgrading system packages..."
apt upgrade -y

# Install required tools
echo "Installing required tools..."
apt install -y rsync unzip

# 3. Install Database Management System (MariaDB - MySQL compatible, uses less memory)
echo "Step 3: Installing MariaDB (MySQL compatible)..."
apt install -y mariadb-server
# Start and enable MariaDB service
systemctl start mariadb
systemctl enable mariadb

# 4. Create Application Database
echo "Step 4: Creating application databases..."
# Create main application database
mysql -e "CREATE DATABASE IF NOT EXISTS healthdb;"
mysql -e "CREATE USER IF NOT EXISTS 'user'@'localhost' IDENTIFIED BY 'pass123';"
mysql -e "GRANT ALL PRIVILEGES ON healthdb.* TO 'user'@'localhost';"

# Create test database for integration tests
mysql -e "CREATE DATABASE IF NOT EXISTS testdb;"
mysql -e "CREATE USER IF NOT EXISTS 'testuser'@'localhost' IDENTIFIED BY 'testpass';"
mysql -e "GRANT ALL PRIVILEGES ON testdb.* TO 'testuser'@'localhost';"

mysql -e "FLUSH PRIVILEGES;"
echo "Databases 'healthdb' and 'testdb' created successfully"

# 5. Create Application Linux Group
echo "Step 5: Creating application group..."
groupadd -f csye6225
echo "Group 'csye6225' created"

# 6. Create Application User Account
echo "Step 6: Creating application user..."
# Create system user (no home directory, no login shell)
useradd -r -g csye6225 -s /usr/sbin/nologin csye6225_user || echo "User already exists"
echo "User 'csye6225_user' created"

# 7. Deploy Application Files
echo "Step 7: Deploying application files..."
# Create application directory
mkdir -p /opt/csye6225

# Navigate to parent directory to get application files
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
APP_DIR="$(dirname "$SCRIPT_DIR")"

cd "$APP_DIR"

# Check if application archive exists
if [ -f "webapp.zip" ]; then
    # Extract application files
    unzip -o webapp.zip -d /opt/csye6225/
    echo "Application files extracted to /opt/csye6225/"
else
    # If no zip file, copy all application files
    # Exclude the scripts folder, .git directory, and other non-application files
    rsync -av \
        --exclude='scripts/' \
        --exclude='.git' \
        --exclude='.github' \
        --exclude='.gitignore' \
        --exclude='README.md' \
        ./ /opt/csye6225/
    echo "Application files copied to /opt/csye6225/"
fi

# 8. Set File Permissions
echo "Step 8: Setting file permissions..."
# Change ownership to application user and group
chown -R csye6225_user:csye6225 /opt/csye6225
# Set directory permissions (755 - owner can read/write/execute, others can read/execute)
find /opt/csye6225 -type d -exec chmod 755 {} \;
# Set file permissions (644 - owner can read/write, others can read)
find /opt/csye6225 -type f -exec chmod 644 {} \;
# If there are any JAR files or scripts, make them executable
find /opt/csye6225 -name "*.jar" -exec chmod 755 {} \; 2>/dev/null || true
find /opt/csye6225 -name "*.sh" -exec chmod 755 {} \; 2>/dev/null || true

# Make Maven wrapper executable if it exists
if [ -f /opt/csye6225/mvnw ]; then
    chmod +x /opt/csye6225/mvnw
    echo "Maven wrapper made executable"
fi

# Set executable permissions for .mvn directory
if [ -d /opt/csye6225/.mvn ]; then
    chmod -R 755 /opt/csye6225/.mvn
fi

echo "Permissions set for /opt/csye6225/"

# Install Java if this is a Spring Boot application
echo "Installing Java for Spring Boot application..."
apt install -y openjdk-17-jdk

# Install Maven (in case mvnw doesn't work)
echo "Installing Maven..."
apt install -y maven

# Build the application
echo "Building the Spring Boot application..."
cd /opt/csye6225
if [ -f "mvnw" ] && [ -x "mvnw" ]; then
    echo "Using Maven wrapper to build..."
    ./mvnw clean package -DskipTests
elif command -v mvn &> /dev/null; then
    echo "Using system Maven to build..."
    mvn clean package -DskipTests
fi

# Find the built JAR file
JAR_FILE=$(find /opt/csye6225/target -name "*.jar" -type f | head -1)
if [ -n "$JAR_FILE" ]; then
    echo "Application JAR built: $JAR_FILE"
else
    echo "Warning: No JAR file found after build"
fi

# Verify installations
echo ""
echo "=== Setup Complete ==="
echo "MariaDB status: $(systemctl is-active mariadb)"
echo "Main database created: healthdb"
echo "Test database created: testdb"
echo "Application user: user (password: pass123)"
echo "Test user: testuser (password: testpass)"
echo "Application group: csye6225"
echo "Application directory: /opt/csye6225/"
echo "Java version: $(java -version 2>&1 | head -n 1)"
if [ -n "$JAR_FILE" ]; then
    echo "Application JAR: $JAR_FILE"
fi
echo ""
echo "=== Quick Validation Commands ==="
echo "To verify main database: mysql -u user -ppass123 -e \"SHOW DATABASES LIKE 'healthdb';\""
echo "To verify test database: mysql -u testuser -ptestpass -e \"SHOW DATABASES LIKE 'testdb';\""
echo "To verify user/group: id csye6225_user && getent group csye6225"
echo "To verify files & permissions: ls -l /opt/csye6225"
echo ""
echo "=== Start Your Application ==="
if [ -n "$JAR_FILE" ]; then
    echo "Run your app: java -jar $JAR_FILE"
    echo "Run in background: nohup java -jar $JAR_FILE > /opt/csye6225/app.log 2>&1 &"
fi
echo ""
echo "=== Next Steps ==="
echo "1. Your application is configured with correct database credentials"
echo "2. Start your application from /opt/csye6225/"
echo "3. Test your API: curl http://localhost:8080/healthz"
echo "======================"