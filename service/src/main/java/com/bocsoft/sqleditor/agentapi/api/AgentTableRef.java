package com.bocsoft.sqleditor.agentapi.api;

public class AgentTableRef {
    private final String catalog;
    private final String schema;
    private final String name;

    public AgentTableRef(String catalog, String schema, String name) {
        this.catalog = catalog;
        this.schema = schema;
        this.name = name;
    }

    public String getCatalog() { return catalog; }
    public String getSchema() { return schema; }
    public String getName() { return name; }
}
