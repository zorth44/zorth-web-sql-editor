package com.bocsoft.sqleditor.execution;

import com.bocsoft.sqleditor.execution.api.SqlColumn;
import com.bocsoft.sqleditor.execution.api.TruncatedValue;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ResultSetReader {
    private final JdbcValueEncoder encoder;
    private final ObjectMapper mapper;

    public ResultSetReader(JdbcValueEncoder encoder, ObjectMapper mapper) {
        this.encoder = encoder;
        this.mapper = mapper;
    }

    public ReadResult read(ResultSet rs, int rowLimit, long byteLimit) throws SQLException {
        return read(rs, rowLimit, byteLimit, Integer.MAX_VALUE);
    }

    public ReadResult read(ResultSet rs, int rowLimit, long byteLimit, int maxCellBytes) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        int count = meta.getColumnCount();
        List<SqlColumn> columns = new ArrayList<SqlColumn>();
        for (int i = 1; i <= count; i++) {
            columns.add(new SqlColumn(meta.getColumnName(i), meta.getColumnLabel(i),
                JdbcTypeNames.name(meta.getColumnType(i)), meta.getColumnTypeName(i)));
        }
        List<List<Object>> rows = new ArrayList<List<Object>>();
        long bytes = 0;
        boolean rowLimitReached = false;
        boolean resultBytesReached = false;
        int cellTruncatedCount = 0;
        while (rs.next()) {
            if (rows.size() >= rowLimit) {
                rowLimitReached = true;
                break;
            }
            List<Object> row = new ArrayList<Object>(count);
            int truncatedInRow = 0;
            for (int i = 1; i <= count; i++) {
                Object encoded = encoder.encode(rs, i, meta.getColumnType(i));
                Object cell = encoded;
                long cellBytes = sizeOf(encoded);
                if (maxCellBytes > 0 && maxCellBytes < Integer.MAX_VALUE && cellBytes > maxCellBytes) {
                    cell = new TruncatedValue(cellBytes);
                    truncatedInRow++;
                }
                row.add(cell);
            }
            long size = sizeOf(row);
            if (bytes + size > byteLimit) {
                resultBytesReached = true;
                break;
            }
            bytes += size;
            cellTruncatedCount += truncatedInRow;
            rows.add(row);
        }
        boolean truncated = rowLimitReached || resultBytesReached || cellTruncatedCount > 0;
        return new ReadResult(columns, rows, truncated, bytes, rowLimitReached, resultBytesReached, cellTruncatedCount);
    }

    private long sizeOf(Object value) throws SQLException {
        try {
            return mapper.writeValueAsBytes(value).length;
        } catch (Exception e) {
            throw new SQLException("结果序列化失败");
        }
    }

    public static class ReadResult {
        private final List<SqlColumn> columns;
        private final List<List<Object>> rows;
        private final boolean truncated;
        private final long bytes;
        private final boolean rowLimitReached;
        private final boolean resultBytesReached;
        private final int cellTruncatedCount;

        public ReadResult(List<SqlColumn> columns, List<List<Object>> rows, boolean truncated, long bytes) {
            this(columns, rows, truncated, bytes, truncated, false, 0);
        }

        public ReadResult(List<SqlColumn> columns, List<List<Object>> rows, boolean truncated, long bytes,
                          boolean rowLimitReached, boolean resultBytesReached, int cellTruncatedCount) {
            this.columns = columns;
            this.rows = rows;
            this.truncated = truncated;
            this.bytes = bytes;
            this.rowLimitReached = rowLimitReached;
            this.resultBytesReached = resultBytesReached;
            this.cellTruncatedCount = cellTruncatedCount;
        }

        public List<SqlColumn> getColumns() { return columns; }
        public List<List<Object>> getRows() { return rows; }
        public boolean isTruncated() { return truncated; }
        public long getBytes() { return bytes; }
        public boolean isRowLimitReached() { return rowLimitReached; }
        public boolean isResultBytesReached() { return resultBytesReached; }
        public int getCellTruncatedCount() { return cellTruncatedCount; }
    }
}
