#!/bin/bash
set -e

echo "=== Starting CloudWatch Agent Setup ==="

# Download CloudWatch Agent for Ubuntu AMD64
wget -q https://s3.amazonaws.com/amazoncloudwatch-agent/ubuntu/amd64/latest/amazon-cloudwatch-agent.deb

# Install CloudWatch Agent
sudo dpkg -i -E ./amazon-cloudwatch-agent.deb

# Remove installer
rm amazon-cloudwatch-agent.deb

# Copy CloudWatch configuration to the agent's config directory
sudo mkdir -p /opt/aws/amazon-cloudwatch-agent/etc
sudo cp /tmp/cloudwatch-config.json /opt/aws/amazon-cloudwatch-agent/etc/cloudwatch-config.json

# Set proper permissions
sudo chown -R cwagent:cwagent /opt/aws/amazon-cloudwatch-agent/etc
sudo chmod 644 /opt/aws/amazon-cloudwatch-agent/etc/cloudwatch-config.json

# CRITICAL: Allow cwagent to read application logs
# Add cwagent to csye6225 group so it can read /var/log/csye6225
sudo usermod -a -G csye6225 cwagent || true

# Ensure group can traverse/read directory
sudo chmod 750 /var/log/csye6225

# Set ACL to ensure cwagent can read log files
sudo setfacl -m g:csye6225:rx /var/log/csye6225 || true
sudo setfacl -m u:cwagent:rx /var/log/csye6225 || true

# Enable CloudWatch Agent service (will be started by user-data)
sudo systemctl enable amazon-cloudwatch-agent.service

# Sanity check - verify agent binary is installed
/opt/aws/amazon-cloudwatch-agent/bin/amazon-cloudwatch-agent-ctl -a status || true

echo "=== CloudWatch Agent Installation Complete ==="
echo "Note: Agent will be started by user-data script at instance launch"