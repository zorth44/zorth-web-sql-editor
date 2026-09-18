package com.bocsoft.sqleditor.metadata.api;

public class TableStats {
    private final String engine, createTime, updateTime, comment;
    private final Long estimatedRows, dataBytes, indexBytes, autoIncrement;

    public TableStats(String engine, Long estimatedRows, Long dataBytes, Long indexBytes, Long autoIncrement,
                      String createTime, String updateTime, String comment) {
        this.engine = engine;
        this.estimatedRows = estimatedRows;
        this.dataBytes = dataBytes;
        this.indexBytes = indexBytes;
        this.autoIncrement = autoIncrement;
        this.createTime = createTime;
        this.updateTime = updateTime;
        this.comment = comment;
    }

    public String getEngine() { return engine; }
    public Long getEstimatedRows() { return estimatedRows; }
    public Long getDataBytes() { return dataBytes; }
    public Long getIndexBytes() { return indexBytes; }
    public Long getAutoIncrement() { return autoIncrement; }
    public String getCreateTime() { return createTime; }
    public String getUpdateTime() { return updateTime; }
    public String getComment() { return comment; }
}
