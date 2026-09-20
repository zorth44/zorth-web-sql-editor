package com.bocsoft.sqleditor.execution;

public final class ExecutionCommand {
    private final String executionId;
    private final String dataSourceId;
    private final String database;
    private final String statement;
    private final int rowLimit;
    private final int timeoutSeconds;
    private final long maxResultBytes;
    private final int maxCellBytes;
    private final boolean readOnly;
    private final String source;
    private final boolean redactDatabaseErrors;

    public ExecutionCommand(String executionId, String dataSourceId, String database, String statement,
                            int rowLimit, int timeoutSeconds, long maxResultBytes, int maxCellBytes,
                            boolean readOnly, String source, boolean redactDatabaseErrors) {
        this.executionId = executionId;
        this.dataSourceId = dataSourceId;
        this.database = database;
        this.statement = statement;
        this.rowLimit = rowLimit;
        this.timeoutSeconds = timeoutSeconds;
        this.maxResultBytes = maxResultBytes;
        this.maxCellBytes = maxCellBytes;
        this.readOnly = readOnly;
        this.source = source;
        this.redactDatabaseErrors = redactDatabaseErrors;
    }

    public String getExecutionId() { return executionId; }
    public String getDataSourceId() { return dataSourceId; }
    public String getDatabase() { return database; }
    public String getStatement() { return statement; }
    public int getRowLimit() { return rowLimit; }
    public int getTimeoutSeconds() { return timeoutSeconds; }
    public long getMaxResultBytes() { return maxResultBytes; }
    public int getMaxCellBytes() { return maxCellBytes; }
    public boolean isReadOnly() { return readOnly; }
    public String getSource() { return source; }
    public boolean isRedactDatabaseErrors() { return redactDatabaseErrors; }
}
