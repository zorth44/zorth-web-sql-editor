package com.bocsoft.sqleditor.agentapi.api;

public class AgentDatabaseItem {
    private final String name;
    private final String kind;

    public AgentDatabaseItem(String name, String kind) {
        this.name = name;
        this.kind = kind;
    }

    public String getName() { return name; }
    public String getKind() { return kind; }
}
