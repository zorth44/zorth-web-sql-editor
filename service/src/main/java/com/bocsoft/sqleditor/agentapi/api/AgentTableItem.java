package com.bocsoft.sqleditor.agentapi.api;

public class AgentTableItem {
    private final String database;
    private final String name;
    private final String type;
    private final String comment;

    public AgentTableItem(String database, String name, String type, String comment) {
        this.database = database;
        this.name = name;
        this.type = type;
        this.comment = comment;
    }

    public String getDatabase() { return database; }
    public String getName() { return name; }
    public String getType() { return type; }
    public String getComment() { return comment; }
}
