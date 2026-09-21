package com.bocsoft.sqleditor.metadata.api;

public final class TableMetadata {
    private final TableDetailResponse detail;
    private final TableConstraintMetadata constraints;

    public TableMetadata(TableDetailResponse detail, TableConstraintMetadata constraints) {
        this.detail = detail;
        this.constraints = constraints;
    }

    public TableDetailResponse getDetail() { return detail; }
    public TableConstraintMetadata getConstraints() { return constraints; }
}
