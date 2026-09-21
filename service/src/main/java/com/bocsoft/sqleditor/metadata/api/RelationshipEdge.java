package com.bocsoft.sqleditor.metadata.api;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Locale;

public final class RelationshipEdge {
    private final String sourceDatabase;
    private final String sourceTable;
    private final List<String> sourceColumns;
    private final String targetDatabase;
    private final String targetTable;
    private final List<String> targetColumns;
    private final String direction;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final String constraintName;
    private final String evidence;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final String cardinality;

    public RelationshipEdge(String sourceDatabase, String sourceTable, List<String> sourceColumns,
                            String targetDatabase, String targetTable, List<String> targetColumns,
                            String direction, String constraintName, String evidence, String cardinality) {
        this.sourceDatabase = sourceDatabase;
        this.sourceTable = sourceTable;
        this.sourceColumns = ConstraintColumns.requirePaired(sourceColumns, targetColumns, "relationship columns");
        this.targetDatabase = targetDatabase;
        this.targetTable = targetTable;
        this.targetColumns = ConstraintColumns.copy(targetColumns);
        this.direction = direction;
        this.constraintName = constraintName;
        this.evidence = evidence;
        this.cardinality = cardinality;
    }

    public String getSourceDatabase() { return sourceDatabase; }
    public String getSourceTable() { return sourceTable; }
    public List<String> getSourceColumns() { return sourceColumns; }
    public String getTargetDatabase() { return targetDatabase; }
    public String getTargetTable() { return targetTable; }
    public List<String> getTargetColumns() { return targetColumns; }
    public String getDirection() { return direction; }
    public String getConstraintName() { return constraintName; }
    public String getEvidence() { return evidence; }
    public String getCardinality() { return cardinality; }

    @JsonIgnore
    public String getSortKey() {
        return join(direction) + '\0' + join(targetDatabase) + '\0' + join(targetTable) + '\0'
            + join(sourceDatabase) + '\0' + join(sourceTable) + '\0' + join(constraintName) + '\0'
            + join(sourceColumns) + '\0' + join(targetColumns);
    }

    private static String join(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private static String join(List<String> values) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) out.append(',');
            out.append(join(values.get(i)));
        }
        return out.toString();
    }
}
