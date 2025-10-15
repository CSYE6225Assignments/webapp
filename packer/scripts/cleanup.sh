#!/bin/bash
set -e

echo "=== Starting Cleanup ==="

# Remove unnecessary packages
sudo apt-get autoremove -y

# Clean package cache
sudo apt-get clean

# Remove temporary files
sudo rm -rf /var/lib/apt/lists/*
sudo rm -rf /tmp/*
sudo rm -rf /var/tmp/*

# Truncate machine-id for unique instance IDs
sudo truncate -s 0 /etc/machine-id || true

# Clear command history
history -c
sudo rm -f /root/.bash_history
sudo rm -f /home/ubuntu/.bash_history

echo "=== Cleanup Complete ==="
