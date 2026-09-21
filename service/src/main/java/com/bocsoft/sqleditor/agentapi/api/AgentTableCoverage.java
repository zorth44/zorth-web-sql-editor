package com.bocsoft.sqleditor.agentapi.api;

public class AgentTableCoverage {
    private final AgentSectionCoverage uniqueKeys;
    private final AgentSectionCoverage foreignKeys;
    private final AgentSectionCoverage indexes;

    public AgentTableCoverage(AgentSectionCoverage uniqueKeys, AgentSectionCoverage foreignKeys,
                              AgentSectionCoverage indexes) {
        this.uniqueKeys = uniqueKeys;
        this.foreignKeys = foreignKeys;
        this.indexes = indexes;
    }

    public AgentSectionCoverage getUniqueKeys() { return uniqueKeys; }
    public AgentSectionCoverage getForeignKeys() { return foreignKeys; }
    public AgentSectionCoverage getIndexes() { return indexes; }
}
