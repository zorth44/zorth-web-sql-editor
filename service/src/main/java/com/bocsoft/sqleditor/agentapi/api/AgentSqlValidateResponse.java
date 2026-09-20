package com.bocsoft.sqleditor.agentapi.api;

import java.util.List;

public class AgentSqlValidateResponse {
    private final boolean valid;
    private final String statementType;
    private final boolean readOnly;
    private final boolean multiStatement;
    private final List<AgentTableRef> tables;
    private final List<AgentFinding> warnings;
    private final List<AgentFinding> violations;

    public AgentSqlValidateResponse(boolean valid, String statementType, boolean readOnly, boolean multiStatement,
                                    List<AgentTableRef> tables, List<AgentFinding> warnings,
                                    List<AgentFinding> violations) {
        this.valid = valid;
        this.statementType = statementType;
        this.readOnly = readOnly;
        this.multiStatement = multiStatement;
        this.tables = tables;
        this.warnings = warnings;
        this.violations = violations;
    }

    public boolean isValid() { return valid; }
    public String getStatementType() { return statementType; }
    public boolean isReadOnly() { return readOnly; }
    public boolean isMultiStatement() { return multiStatement; }
    public List<AgentTableRef> getTables() { return tables; }
    public List<AgentFinding> getWarnings() { return warnings; }
    public List<AgentFinding> getViolations() { return violations; }
}
