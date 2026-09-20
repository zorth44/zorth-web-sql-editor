package com.bocsoft.sqleditor.execution;

public final class SqlTableRef {
    private final String catalog;
    private final String schema;
    private final String name;

    public SqlTableRef(String catalog, String schema, String name) {
        this.catalog = catalog;
        this.schema = schema;
        this.name = name;
    }

    public String getCatalog() { return catalog; }
    public String getSchema() { return schema; }
    public String getName() { return name; }
}
