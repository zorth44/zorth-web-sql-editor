package com.bocsoft.sqleditor.metadata.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

public final class ForeignKeyItem {
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final String constraintName;
    private final List<String> columns;
    private final String targetDatabase;
    private final String targetTable;
    private final List<String> targetColumns;
    private final String evidence;

    public ForeignKeyItem(String constraintName, List<String> columns, String targetDatabase, String targetTable,
                          List<String> targetColumns, String evidence) {
        this.constraintName = constraintName;
        this.columns = ConstraintColumns.requirePaired(columns, targetColumns, "foreign key columns");
        this.targetDatabase = targetDatabase;
        this.targetTable = targetTable;
        this.targetColumns = ConstraintColumns.copy(targetColumns);
        this.evidence = evidence;
    }

    public String getConstraintName() { return constraintName; }
    public List<String> getColumns() { return columns; }
    public String getTargetDatabase() { return targetDatabase; }
    public String getTargetTable() { return targetTable; }
    public List<String> getTargetColumns() { return targetColumns; }
    public String getReferencedDatabase() { return targetDatabase; }
    public String getReferencedTable() { return targetTable; }
    public List<String> getReferencedColumns() { return targetColumns; }
    public String getEvidence() { return evidence; }
}
