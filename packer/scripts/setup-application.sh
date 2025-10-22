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

# DO NOT create application.properties here
# It will be created by user-data script with RDS configuration

# Set ownership
sudo chown -R csye6225:csye6225 /opt/csye6225

# Set permissions
sudo chmod 750 /opt/csye6225
sudo chmod 640 /opt/csye6225/application.jar

echo "=== Application Setup Complete ==="
echo "Note: application.properties will be created by EC2 user-data"