ALTER TABLE sql_execution_history
  ADD COLUMN source varchar(20) NOT NULL DEFAULT 'WEB_SQL_EDITOR' AFTER operation;
