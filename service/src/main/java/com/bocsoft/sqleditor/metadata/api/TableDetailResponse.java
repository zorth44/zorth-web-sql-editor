package com.bocsoft.sqleditor.metadata.api;
import java.util.List;
public class TableDetailResponse {
    private final String database, table, ddl;
    private final List<ColumnItem> columns;
    private final PrimaryKeyItem primaryKey;
    private final List<IndexItem> indexes;
    public TableDetailResponse(String database, String table, List<ColumnItem> columns, PrimaryKeyItem primaryKey, List<IndexItem> indexes, String ddl) {
        this.database = database;
        this.table = table;
        this.columns = columns;
        this.primaryKey = primaryKey;
        this.indexes = indexes;
        this.ddl = ddl;
    }
    public String getDatabase() { return database; }
    public String getTable() { return table; }
    public List<ColumnItem> getColumns() { return columns; }
    public PrimaryKeyItem getPrimaryKey() { return primaryKey; }
    public List<IndexItem> getIndexes() { return indexes; }
    public String getDdl() { return ddl; }
}
