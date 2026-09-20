package com.bocsoft.sqleditor.execution;

import java.util.Collections;
import java.util.List;

public final class SqlSafetyAssessment {
    private final boolean valid;
    private final String statementType;
    private final boolean readOnly;
    private final boolean multiStatement;
    private final List<SqlTableRef> tables;
    private final List<SqlFinding> warnings;
    private final List<SqlFinding> violations;
    private final String sql;

    public SqlSafetyAssessment(boolean valid, String statementType, boolean readOnly, boolean multiStatement,
                               List<SqlTableRef> tables, List<SqlFinding> warnings, List<SqlFinding> violations,
                               String sql) {
        this.valid = valid;
        this.statementType = statementType;
        this.readOnly = readOnly;
        this.multiStatement = multiStatement;
        this.tables = tables == null ? Collections.<SqlTableRef>emptyList() : tables;
        this.warnings = warnings == null ? Collections.<SqlFinding>emptyList() : warnings;
        this.violations = violations == null ? Collections.<SqlFinding>emptyList() : violations;
        this.sql = sql;
    }

    public boolean isValid() { return valid; }
    public String getStatementType() { return statementType; }
    public boolean isReadOnly() { return readOnly; }
    public boolean isMultiStatement() { return multiStatement; }
    public List<SqlTableRef> getTables() { return tables; }
    public List<SqlFinding> getWarnings() { return warnings; }
    public List<SqlFinding> getViolations() { return violations; }
    public String getSql() { return sql; }
}
