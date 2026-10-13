-- Local development bootstrap for the Zorth Web SQL Editor.
-- Idempotent: safe to run on every install. Creates the SQL service metadata
-- database plus a seeded demo target database that can be added as a data
-- source from the editor UI. These credentials are LOCAL DEVELOPMENT ONLY and
-- mirror the published examples in docs/local-development.md.

-- Metadata database used by the SQL service (matches application.yml defaults).
CREATE DATABASE IF NOT EXISTS sqleditor CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'sqleditor_user'@'127.0.0.1' IDENTIFIED WITH caching_sha2_password BY 'sqleditor_password';
CREATE USER IF NOT EXISTS 'sqleditor_user'@'localhost' IDENTIFIED WITH caching_sha2_password BY 'sqleditor_password';
GRANT ALL PRIVILEGES ON sqleditor.* TO 'sqleditor_user'@'127.0.0.1';
GRANT ALL PRIVILEGES ON sqleditor.* TO 'sqleditor_user'@'localhost';

-- Demo target database the editor can connect to as a data source.
CREATE DATABASE IF NOT EXISTS demo_shop CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'demo_user'@'127.0.0.1' IDENTIFIED WITH caching_sha2_password BY 'demo_pass_12345';
CREATE USER IF NOT EXISTS 'demo_user'@'localhost' IDENTIFIED WITH caching_sha2_password BY 'demo_pass_12345';
GRANT ALL PRIVILEGES ON demo_shop.* TO 'demo_user'@'127.0.0.1';
GRANT ALL PRIVILEGES ON demo_shop.* TO 'demo_user'@'localhost';
FLUSH PRIVILEGES;

USE demo_shop;
CREATE TABLE IF NOT EXISTS customers (
  id INT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(100) NOT NULL,
  email VARCHAR(200) NOT NULL,
  city VARCHAR(80),
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS orders (
  id INT PRIMARY KEY AUTO_INCREMENT,
  customer_id INT NOT NULL,
  amount DECIMAL(10,2) NOT NULL,
  status VARCHAR(20) NOT NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- Seed deterministic sample rows only once (guarded so re-runs stay idempotent).
INSERT INTO customers (name, email, city)
SELECT * FROM (
  SELECT 'Alice Chen' AS name, 'alice@example.com' AS email, 'Shanghai' AS city UNION ALL
  SELECT 'Bob Li', 'bob@example.com', 'Beijing' UNION ALL
  SELECT 'Carol Wang', 'carol@example.com', 'Shenzhen' UNION ALL
  SELECT 'David Zhang', 'david@example.com', 'Hangzhou'
) seed
WHERE NOT EXISTS (SELECT 1 FROM customers);

INSERT INTO orders (customer_id, amount, status)
SELECT * FROM (
  SELECT 1 AS customer_id, 129.90 AS amount, 'PAID' AS status UNION ALL
  SELECT 1, 59.00, 'PAID' UNION ALL
  SELECT 2, 999.00, 'PENDING' UNION ALL
  SELECT 3, 12.50, 'REFUNDED' UNION ALL
  SELECT 4, 340.00, 'PAID'
) seed
WHERE NOT EXISTS (SELECT 1 FROM orders);
