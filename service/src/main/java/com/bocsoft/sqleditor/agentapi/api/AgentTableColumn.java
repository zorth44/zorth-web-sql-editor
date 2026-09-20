package com.bocsoft.sqleditor.agentapi.api;

public class AgentTableColumn {
    private final String name;
    private final String jdbcType;
    private final String typeName;
    private final Integer length;
    private final Integer precision;
    private final Integer scale;
    private final boolean nullable;
    private final boolean primaryKey;
    private final String defaultValue;
    private final String extra;
    private final String comment;
    private final Integer ordinal;

    public AgentTableColumn(String name, String jdbcType, String typeName, Integer length, Integer precision,
                            Integer scale, boolean nullable, boolean primaryKey, String defaultValue, String extra,
                            String comment, Integer ordinal) {
        this.name = name;
        this.jdbcType = jdbcType;
        this.typeName = typeName;
        this.length = length;
        this.precision = precision;
        this.scale = scale;
        this.nullable = nullable;
        this.primaryKey = primaryKey;
        this.defaultValue = defaultValue;
        this.extra = extra;
        this.comment = comment;
        this.ordinal = ordinal;
    }

    public String getName() { return name; }
    public String getJdbcType() { return jdbcType; }
    public String getTypeName() { return typeName; }
    public Integer getLength() { return length; }
    public Integer getPrecision() { return precision; }
    public Integer getScale() { return scale; }
    public boolean isNullable() { return nullable; }
    public boolean isPrimaryKey() { return primaryKey; }
    public String getDefaultValue() { return defaultValue; }
    public String getExtra() { return extra; }
    public String getComment() { return comment; }
    public Integer getOrdinal() { return ordinal; }
}
