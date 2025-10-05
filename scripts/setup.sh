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
DB_PASS="${DB_PASS:-YourStrongPassword123!}"

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

# 3. Install Database Management System (MySQL)
echo "Step 3: Installing MySQL..."
apt install -y mysql-server
# Start and enable MySQL service
systemctl start mysql
systemctl enable mysql

# 4. Create Application Database
echo "Step 4: Creating application database..."
mysql -e "CREATE DATABASE IF NOT EXISTS csye6225_db;"
mysql -e "CREATE USER IF NOT EXISTS 'csye6225_user'@'localhost' IDENTIFIED BY '${DB_PASS}';"
mysql -e "GRANT ALL PRIVILEGES ON csye6225_db.* TO 'csye6225_user'@'localhost';"
mysql -e "FLUSH PRIVILEGES;"
echo "Database 'csye6225_db' created successfully"

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

echo "Permissions set for /opt/csye6225/"

# Install Java if this is a Spring Boot application
echo "Installing Java for Spring Boot application..."
apt install -y openjdk-17-jdk

# Verify installations
echo ""
echo "=== Setup Complete ==="
echo "MySQL status: $(systemctl is-active mysql)"
echo "Database created: csye6225_db"
echo "Application user: csye6225_user"
echo "Application group: csye6225"
echo "Application directory: /opt/csye6225/"
echo "Java version: $(java -version 2>&1 | head -n 1)"
echo ""
echo "=== Quick Validation Commands ==="
echo "To verify database: mysql -u csye6225_user -p -e \"SHOW DATABASES LIKE 'csye6225_db';\""
echo "To verify user/group: id csye6225_user && getent group csye6225"
echo "To verify files & permissions: ls -l /opt/csye6225"
echo ""
echo "=== Next Steps ==="
echo "1. Update database password in your application configuration"
echo "   Password used: ${DB_PASS}"
echo "2. Start your application from /opt/csye6225/"
echo "======================"