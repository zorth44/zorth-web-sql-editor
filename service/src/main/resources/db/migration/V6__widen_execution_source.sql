ALTER TABLE sql_execution_history
  MODIFY COLUMN source varchar(32) NOT NULL DEFAULT 'WEB_SQL_EDITOR';
