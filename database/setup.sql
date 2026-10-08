-- MBU Digital Library: one-time database setup.
-- Run this in MySQL Workbench as the root user (or any admin user).
-- It creates an empty database and a dedicated user for the app.
--
-- BEFORE RUNNING: replace CHANGE_ME_TO_A_STRONG_PASSWORD below with your own password,
-- then put the same password in config/application.properties (see README).

CREATE DATABASE IF NOT EXISTS mbu_library
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

CREATE USER IF NOT EXISTS 'mbu_app'@'localhost' IDENTIFIED BY 'CHANGE_ME_TO_A_STRONG_PASSWORD';

GRANT ALL PRIVILEGES ON mbu_library.* TO 'mbu_app'@'localhost';
FLUSH PRIVILEGES;
