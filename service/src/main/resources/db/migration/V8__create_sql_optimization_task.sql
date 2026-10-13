CREATE TABLE sql_optimization_quota_lock (
  id INT NOT NULL PRIMARY KEY
) ENGINE=InnoDB;
INSERT INTO sql_optimization_quota_lock (id) VALUES (1);

CREATE TABLE sql_optimization_task (
  id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
  user_id VARCHAR(100) COLLATE utf8mb4_bin NOT NULL,
  product_id VARCHAR(100) COLLATE utf8mb4_bin NOT NULL,
  name VARCHAR(100) NOT NULL,
  data_source_id VARCHAR(36) NOT NULL,
  data_source_name VARCHAR(100) NOT NULL,
  engine_id VARCHAR(40) NOT NULL,
  database_name VARCHAR(256) NOT NULL,
  statement_text MEDIUMTEXT NOT NULL,
  statement_sha256 CHAR(64) NOT NULL,
  source_task_id VARCHAR(36) NULL,
  idempotency_key VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  request_fingerprint CHAR(64) NOT NULL,
  status VARCHAR(16) NOT NULL,
  current_attempt_id VARCHAR(36) NULL,
  version BIGINT NOT NULL DEFAULT 1,
  report_schema_version INT NULL,
  report_json MEDIUMTEXT NULL,
  policy_issue_count INT NULL,
  performance_issue_count INT NULL,
  limitation_count INT NULL,
  created_at DATETIME(6) NOT NULL,
  started_at DATETIME(6) NULL,
  completed_at DATETIME(6) NULL,
  expires_at DATETIME(6) NOT NULL,
  error_code VARCHAR(64) NULL,
  error_message VARCHAR(300) NULL,
  request_id VARCHAR(36) NOT NULL,
  UNIQUE KEY uk_optimization_idempotency (user_id, product_id, idempotency_key),
  KEY idx_optimization_owner (user_id, product_id, created_at, id),
  KEY idx_optimization_queue (status, created_at, id),
  KEY idx_optimization_retention (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE sql_optimization_attempt (
  id VARCHAR(36) NOT NULL PRIMARY KEY,
  task_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  attempt_no INT NOT NULL,
  session_id VARCHAR(36) NOT NULL,
  request_id VARCHAR(36) NOT NULL,
  worker_id VARCHAR(36) NOT NULL,
  lease_until DATETIME(6) NOT NULL,
  hard_deadline DATETIME(6) NOT NULL,
  started_at DATETIME(6) NOT NULL,
  completed_at DATETIME(6) NULL,
  outcome VARCHAR(16) NULL,
  error_code VARCHAR(64) NULL,
  UNIQUE KEY uk_optimization_attempt (task_id, attempt_no),
  KEY idx_optimization_lease (lease_until, completed_at),
  CONSTRAINT fk_optimization_attempt_task FOREIGN KEY (task_id) REFERENCES sql_optimization_task(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE sql_optimization_credential (
  task_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
  ciphertext TEXT NOT NULL,
  key_version VARCHAR(64) NOT NULL,
  expires_at DATETIME(6) NOT NULL,
  KEY idx_optimization_credential_expiry (expires_at),
  CONSTRAINT fk_optimization_credential_task FOREIGN KEY (task_id) REFERENCES sql_optimization_task(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
