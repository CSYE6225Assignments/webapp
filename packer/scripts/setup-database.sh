#!/bin/bash
set -e

echo "=== Starting Database Setup ==="

# Install MariaDB
sudo apt-get update -y
sudo apt-get install -y mariadb-server

# Configure MariaDB for UTC and strict mode
sudo tee /etc/mysql/mariadb.conf.d/60-csye6225.cnf >/dev/null <<'SQLEOF'
[mysqld]
default_time_zone = '+00:00'
sql_mode = STRICT_ALL_TABLES,ERROR_FOR_DIVISION_BY_ZERO,NO_ZERO_DATE,NO_ZERO_IN_DATE
character-set-server = utf8mb4
collation-server = utf8mb4_unicode_ci
SQLEOF

# Start MariaDB
sudo systemctl start mariadb
sudo systemctl enable mariadb

# Wait for MariaDB to be ready
sleep 5

# Restart MariaDB to apply configuration
sudo systemctl restart mariadb
sleep 3

# Create database and user with proper charset
sudo mysql <<SQL
CREATE DATABASE IF NOT EXISTS \`${MYSQL_DATABASE}\`
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS '${MYSQL_USER}'@'localhost' IDENTIFIED BY '${MYSQL_PASSWORD}';
GRANT ALL PRIVILEGES ON \`${MYSQL_DATABASE}\`.* TO '${MYSQL_USER}'@'localhost';
FLUSH PRIVILEGES;
SQL

# Verify database creation
sudo mysql -e "SHOW DATABASES;" | grep -q "${MYSQL_DATABASE}" && echo "Database ${MYSQL_DATABASE} created successfully"
sudo mysql -e "SELECT @@time_zone, @@sql_mode;" && echo "MariaDB timezone and SQL mode configured"

echo "=== Database Setup Complete ==="
