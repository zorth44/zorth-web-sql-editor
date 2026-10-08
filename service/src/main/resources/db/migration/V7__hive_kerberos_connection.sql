ALTER TABLE sql_data_source
  MODIFY COLUMN host varchar(255) NULL,
  MODIFY COLUMN port int NULL,
  MODIFY COLUMN username varchar(128) NULL,
  MODIFY COLUMN password_ciphertext text NULL,
  MODIFY COLUMN password_iv varchar(64) NULL,
  MODIFY COLUMN key_version varchar(32) NULL,
  MODIFY COLUMN ssl_mode varchar(20) NULL,
  ADD COLUMN environment varchar(32) NULL,
  ADD COLUMN keytab_file varchar(255) NULL,
  ADD COLUMN queue_name varchar(128) NULL;
