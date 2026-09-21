package com.bocsoft.sqleditor.agentapi.api;

import com.fasterxml.jackson.annotation.JsonInclude;

public class AgentTableLimits {
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final Integer uniqueKeys;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final Integer foreignKeys;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final Integer indexes;

    public AgentTableLimits(Integer uniqueKeys, Integer foreignKeys, Integer indexes) {
        this.uniqueKeys = uniqueKeys;
        this.foreignKeys = foreignKeys;
        this.indexes = indexes;
    }

    public Integer getUniqueKeys() { return uniqueKeys; }
    public Integer getForeignKeys() { return foreignKeys; }
    public Integer getIndexes() { return indexes; }
}
