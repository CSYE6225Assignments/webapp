#!/bin/bash
set -e

echo "=== Starting Service Setup ==="

# Create systemd service file
sudo tee /etc/systemd/system/csye6225.service > /dev/null <<'SERVICEEOF'
[Unit]
Description=CSYE6225 Spring Boot Application
After=network-online.target mariadb.service
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
SERVICEEOF

# Reload systemd
sudo systemctl daemon-reload

# Enable service to start on boot
sudo systemctl enable csye6225.service

# Verify service is enabled
systemctl is-enabled csye6225.service

echo "=== Service Setup Complete ==="
