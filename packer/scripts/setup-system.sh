#!/bin/bash
set -e

echo "=== Starting System Setup ==="

# Update package lists
sudo apt-get update -y

# Upgrade existing packages
sudo apt-get upgrade -y

# Install required packages (use JRE headless instead of full JDK)
sudo apt-get install -y \
    openjdk-17-jre-headless \
    curl \
    wget \
    unzip

# Verify Java installation
java -version

echo "=== System Setup Complete ==="
