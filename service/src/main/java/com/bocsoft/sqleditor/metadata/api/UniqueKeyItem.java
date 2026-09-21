package com.bocsoft.sqleditor.metadata.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

public final class UniqueKeyItem {
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final String name;
    private final List<String> columns;
    private final String evidence;

    public UniqueKeyItem(String name, List<String> columns, String evidence) {
        this.name = name;
        this.columns = ConstraintColumns.requireNonempty(columns, "unique key columns");
        this.evidence = evidence;
    }

    public String getName() { return name; }
    public List<String> getColumns() { return columns; }
    public String getEvidence() { return evidence; }
}
