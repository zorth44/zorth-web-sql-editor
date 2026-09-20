package com.bocsoft.sqleditor.execution;

import com.bocsoft.sqleditor.execution.api.SqlExecutionResponse;

public final class ExecutionOutcome {
    private final SqlExecutionResponse response;
    private final ResultSetReader.ReadResult read;

    public ExecutionOutcome(SqlExecutionResponse response, ResultSetReader.ReadResult read) {
        this.response = response;
        this.read = read;
    }

    public SqlExecutionResponse getResponse() { return response; }
    public ResultSetReader.ReadResult getRead() { return read; }
}
