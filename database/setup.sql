-- MBU Digital Library: one-time database setup.
-- Run this in MySQL Workbench as the root user (or any admin user).
-- It creates an empty database and a dedicated user for the app.

CREATE DATABASE IF NOT EXISTS mbu_library
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

-- Change the password below if you like, then tell me to use the same one
-- (or put it in application.properties yourself).
CREATE USER IF NOT EXISTS 'mbu_app'@'localhost' IDENTIFIED BY 'MbuApp@2026';

GRANT ALL PRIVILEGES ON mbu_library.* TO 'mbu_app'@'localhost';
FLUSH PRIVILEGES;
