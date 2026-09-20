package com.bocsoft.sqleditor.metadata.api;

public class ColumnSearchItem {
    private final String database;
    private final String table;
    private final String name;
    private final String jdbcType;
    private final String typeName;
    private final boolean nullable;
    private final String comment;

    public ColumnSearchItem(String database, String table, String name, String jdbcType, String typeName,
                            boolean nullable, String comment) {
        this.database = database;
        this.table = table;
        this.name = name;
        this.jdbcType = jdbcType;
        this.typeName = typeName;
        this.nullable = nullable;
        this.comment = comment;
    }

    public String getDatabase() { return database; }
    public String getTable() { return table; }
    public String getName() { return name; }
    public String getJdbcType() { return jdbcType; }
    public String getTypeName() { return typeName; }
    public boolean isNullable() { return nullable; }
    public String getComment() { return comment; }

    public String cursorKey() {
        return table + '\0' + name;
    }
}
