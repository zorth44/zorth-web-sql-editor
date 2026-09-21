package com.bocsoft.sqleditor.agentapi.api;

import com.bocsoft.sqleditor.metadata.api.ForeignKeyItem;
import com.bocsoft.sqleditor.metadata.api.IndexItem;
import com.bocsoft.sqleditor.metadata.api.PrimaryKeyItem;
import com.bocsoft.sqleditor.metadata.api.TableStats;
import com.bocsoft.sqleditor.metadata.api.UniqueKeyItem;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class AgentTableDetail {
    private final String database;
    private final String table;
    private final List<AgentTableColumn> columns;
    private final PrimaryKeyItem primaryKey;
    private final List<UniqueKeyItem> uniqueKeys;
    private final List<ForeignKeyItem> foreignKeys;
    private final List<IndexItem> indexes;
    private final String ddl;
    private final TableStats stats;
    private final AgentTableCoverage coverage;
    private final AgentTableLimits appliedLimits;

    public AgentTableDetail(String database, String table, List<AgentTableColumn> columns, PrimaryKeyItem primaryKey,
                            List<UniqueKeyItem> uniqueKeys, List<ForeignKeyItem> foreignKeys, List<IndexItem> indexes,
                            String ddl, TableStats stats, AgentTableCoverage coverage, AgentTableLimits appliedLimits) {
        this.database = database;
        this.table = table;
        this.columns = columns;
        this.primaryKey = primaryKey;
        this.uniqueKeys = uniqueKeys;
        this.foreignKeys = foreignKeys;
        this.indexes = indexes;
        this.ddl = ddl;
        this.stats = stats;
        this.coverage = coverage;
        this.appliedLimits = appliedLimits;
    }

    public String getDatabase() { return database; }
    public String getTable() { return table; }
    public List<AgentTableColumn> getColumns() { return columns; }
    public PrimaryKeyItem getPrimaryKey() { return primaryKey; }
    public List<UniqueKeyItem> getUniqueKeys() { return uniqueKeys; }
    public List<ForeignKeyItem> getForeignKeys() { return foreignKeys; }
    public List<IndexItem> getIndexes() { return indexes; }
    public String getDdl() { return ddl; }
    public TableStats getStats() { return stats; }
    public AgentTableCoverage getCoverage() { return coverage; }
    public AgentTableLimits getAppliedLimits() { return appliedLimits; }
}
