package com.bocsoft.sqleditor.agentapi.api;

import com.bocsoft.sqleditor.metadata.api.IndexItem;
import com.bocsoft.sqleditor.metadata.api.PrimaryKeyItem;
import com.bocsoft.sqleditor.metadata.api.TableStats;
import java.util.List;

public class AgentTableDetail {
    private final String database;
    private final String table;
    private final List<AgentTableColumn> columns;
    private final PrimaryKeyItem primaryKey;
    private final List<IndexItem> indexes;
    private final String ddl;
    private final TableStats stats;

    public AgentTableDetail(String database, String table, List<AgentTableColumn> columns, PrimaryKeyItem primaryKey,
                            List<IndexItem> indexes, String ddl, TableStats stats) {
        this.database = database;
        this.table = table;
        this.columns = columns;
        this.primaryKey = primaryKey;
        this.indexes = indexes;
        this.ddl = ddl;
        this.stats = stats;
    }

    public String getDatabase() { return database; }
    public String getTable() { return table; }
    public List<AgentTableColumn> getColumns() { return columns; }
    public PrimaryKeyItem getPrimaryKey() { return primaryKey; }
    public List<IndexItem> getIndexes() { return indexes; }
    public String getDdl() { return ddl; }
    public TableStats getStats() { return stats; }
}
