package com.bocsoft.sqleditor.script.api;

import com.bocsoft.sqleditor.script.persistence.ScriptRecord;

public class ScriptDetail extends ScriptSummary {
    private final String statement;
    private final boolean connectionAvailable;

    public ScriptDetail(ScriptRecord record, boolean connectionAvailable) {
        super(record);
        this.statement = record.getStatementText();
        this.connectionAvailable = connectionAvailable;
    }

    public String getStatement() { return statement; }
    public boolean isConnectionAvailable() { return connectionAvailable; }
}
