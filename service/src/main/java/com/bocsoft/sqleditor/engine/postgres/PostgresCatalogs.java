package com.bocsoft.sqleditor.engine.postgres;

import com.bocsoft.sqleditor.common.ApiException;
import com.bocsoft.sqleditor.engine.EngineId;
import com.bocsoft.sqleditor.metadata.api.ColumnItem;
import com.bocsoft.sqleditor.metadata.api.ColumnSearchItem;
import com.bocsoft.sqleditor.metadata.api.DatabaseItem;
import com.bocsoft.sqleditor.metadata.api.IndexItem;
import com.bocsoft.sqleditor.metadata.api.PrimaryKeyItem;
import com.bocsoft.sqleditor.metadata.api.TableDetailResponse;
import com.bocsoft.sqleditor.metadata.api.TableItem;
import com.bocsoft.sqleditor.metadata.api.TableStats;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.springframework.http.HttpStatus;

final class PostgresCatalogs {
    private static final Map<Integer, String> JDBC_NAMES;
    static {
        Map<Integer, String> names = new HashMap<Integer, String>();
        for (Field field : Types.class.getFields()) {
            try {
                if (Modifier.isStatic(field.getModifiers()) && field.getType() == int.class) names.put(field.getInt(null), field.getName());
            } catch (Exception ignored) { }
        }
        JDBC_NAMES = Collections.unmodifiableMap(names);
    }

    void validateIdentifier(String field, String value) {
        if (value == null || value.trim().isEmpty() || value.length() > 63 || value.indexOf('\0') >= 0) {
            throw ApiException.validation(field, "INVALID", "标识符不合法");
        }
    }

    List<DatabaseItem> listDatabases(Connection connection, String keyword, boolean includeSystem) throws SQLException {
        List<DatabaseItem> out = new ArrayList<DatabaseItem>();
        try (ResultSet rs = connection.getMetaData().getSchemas()) {
            while (rs.next()) {
                String name = rs.getString("TABLE_SCHEM");
                if (name == null) continue;
                if ((includeSystem || !system(name)) && contains(name, keyword)) {
                    out.add(new DatabaseItem(name));
                }
            }
        }
        out.sort(Comparator.comparing(DatabaseItem::getName, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    List<TableItem> listTables(Connection connection, String database, String keyword, String[] types) throws SQLException {
        ensureNamespace(connection, database);
        List<TableItem> out = new ArrayList<TableItem>();
        try (ResultSet rs = connection.getMetaData().getTables(null, database, "%", types)) {
            while (rs.next()) {
                String name = rs.getString("TABLE_NAME");
                if (contains(name, keyword)) {
                    out.add(new TableItem(database, name, normalizeTableType(rs.getString("TABLE_TYPE")), rs.getString("REMARKS")));
                }
            }
        }
        out.sort(Comparator.comparing(TableItem::getName, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    TableDetailResponse tableDetail(Connection connection, String database, String table) throws SQLException {
        ensureNamespace(connection, database);
        DatabaseMetaData meta = connection.getMetaData();
        Map<Short, String> pkOrder = new TreeMap<Short, String>();
        String pkName = null;
        try (ResultSet rs = meta.getPrimaryKeys(null, database, table)) {
            while (rs.next()) {
                pkName = rs.getString("PK_NAME");
                pkOrder.put(rs.getShort("KEY_SEQ"), rs.getString("COLUMN_NAME"));
            }
        }
        Set<String> pkSet = new HashSet<String>(pkOrder.values());
        List<ColumnItem> columns = new ArrayList<ColumnItem>();
        try (ResultSet rs = meta.getColumns(null, database, table, "%")) {
            while (rs.next()) {
                columns.add(new ColumnItem(
                    rs.getString("COLUMN_NAME"),
                    rs.getString("TYPE_NAME"),
                    jdbcTypeName(rs.getInt("DATA_TYPE")),
                    integer(rs, "COLUMN_SIZE"),
                    integer(rs, "COLUMN_SIZE"),
                    integer(rs, "DECIMAL_DIGITS"),
                    rs.getInt("NULLABLE") != DatabaseMetaData.columnNoNulls,
                    rs.getString("COLUMN_DEF"),
                    rs.getString("IS_AUTOINCREMENT"),
                    rs.getString("REMARKS"),
                    integer(rs, "ORDINAL_POSITION"),
                    pkSet.contains(rs.getString("COLUMN_NAME"))
                ));
            }
        }
        if (columns.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "TABLE_NOT_FOUND", "表不存在或已不可见");
        Map<String, IndexAccumulator> idx = new LinkedHashMap<String, IndexAccumulator>();
        try (ResultSet rs = meta.getIndexInfo(null, database, table, false, false)) {
            while (rs.next()) {
                String name = rs.getString("INDEX_NAME"), column = rs.getString("COLUMN_NAME");
                if (name == null || column == null) continue;
                IndexAccumulator accumulator = idx.get(name);
                if (accumulator == null) {
                    accumulator = new IndexAccumulator(name, !rs.getBoolean("NON_UNIQUE"), indexType(rs.getShort("TYPE")));
                    idx.put(name, accumulator);
                }
                accumulator.columns.put(rs.getShort("ORDINAL_POSITION"), column);
            }
        }
        List<IndexItem> indexes = new ArrayList<IndexItem>();
        for (IndexAccumulator accumulator : idx.values()) {
            indexes.add(new IndexItem(accumulator.name, accumulator.unique, accumulator.type, new ArrayList<String>(accumulator.columns.values())));
        }
        return new TableDetailResponse(
            database, table, columns,
            pkOrder.isEmpty() ? null : new PrimaryKeyItem(pkName, new ArrayList<String>(pkOrder.values())),
            indexes, readDdl(connection, database, table, columns), readStats(connection, database, table)
        );
    }

    List<ColumnSearchItem> searchColumns(Connection connection, String database, String keyword) throws SQLException {
        ensureNamespace(connection, database);
        String like = "%" + escapeLike(keyword) + "%";
        String sql = "SELECT c.relname AS table_name, a.attname AS column_name, "
            + "pg_catalog.format_type(a.atttypid, a.atttypmod) AS type_name, "
            + "NOT a.attnotnull AS nullable, "
            + "col_description(c.oid, a.attnum) AS comment, t.typname AS data_type "
            + "FROM pg_catalog.pg_attribute a "
            + "JOIN pg_catalog.pg_class c ON c.oid = a.attrelid "
            + "JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace "
            + "JOIN pg_catalog.pg_type t ON t.oid = a.atttypid "
            + "WHERE n.nspname = ? AND a.attnum > 0 AND NOT a.attisdropped "
            + "AND c.relkind IN ('r','p','v','m','f') "
            + "AND (a.attname ILIKE ? ESCAPE '!' OR COALESCE(col_description(c.oid, a.attnum),'') ILIKE ? ESCAPE '!') "
            + "ORDER BY c.relname, a.attname";
        List<ColumnSearchItem> out = new ArrayList<ColumnSearchItem>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, database);
            statement.setString(2, like);
            statement.setString(3, like);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    out.add(new ColumnSearchItem(
                        database,
                        rs.getString("table_name"),
                        rs.getString("column_name"),
                        jdbcTypeNameFromPg(rs.getString("data_type")),
                        rs.getString("type_name"),
                        rs.getBoolean("nullable"),
                        rs.getString("comment")
                    ));
                }
            }
        }
        return out;
    }

    void ensureNamespace(Connection connection, String database) throws SQLException {
        try (ResultSet rs = connection.getMetaData().getSchemas()) {
            while (rs.next()) {
                if (database.equals(rs.getString("TABLE_SCHEM"))) {
                    connection.setSchema(database);
                    return;
                }
            }
        }
        throw new ApiException(HttpStatus.NOT_FOUND, "DATABASE_NOT_FOUND", "模式不存在或已不可见");
    }

    void applyNamespace(Connection connection, String namespace) throws SQLException {
        if (hasText(namespace)) connection.setSchema(namespace);
    }

    boolean restoreSession(Connection connection, String defaultNamespace) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("SET search_path TO DEFAULT");
        }
        return false;
    }

    void verifyDefaultNamespace(Connection connection, String defaultNamespace) throws SQLException {
        if (!hasText(defaultNamespace)) {
            throw new SQLException("Default database unavailable", "3D000", 0);
        }
    }

    String quoteIdentifier(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private String readDdl(Connection connection, String database, String table, List<ColumnItem> columns) {
        String view = readViewDdl(connection, database, table);
        if (view != null) return view;
        StringBuilder ddl = new StringBuilder();
        ddl.append("CREATE TABLE ").append(quoteIdentifier(database)).append('.').append(quoteIdentifier(table)).append(" (\n");
        for (int i = 0; i < columns.size(); i++) {
            ColumnItem column = columns.get(i);
            ddl.append("  ").append(quoteIdentifier(column.getName())).append(' ').append(column.getTypeName());
            if (!column.isNullable()) ddl.append(" NOT NULL");
            if (i + 1 < columns.size()) ddl.append(',');
            ddl.append('\n');
        }
        ddl.append(')');
        return ddl.toString();
    }

    private String readViewDdl(Connection connection, String database, String table) {
        String sql = "SELECT pg_catalog.pg_get_viewdef(c.oid, true) FROM pg_catalog.pg_class c "
            + "JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace "
            + "WHERE n.nspname = ? AND c.relname = ? AND c.relkind IN ('v','m')";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, database);
            statement.setString(2, table);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    String body = rs.getString(1);
                    if (body != null && !body.trim().isEmpty()) {
                        return "CREATE VIEW " + quoteIdentifier(database) + "." + quoteIdentifier(table) + " AS\n" + body;
                    }
                }
            }
        } catch (SQLException ignored) { }
        return null;
    }

    private TableStats readStats(Connection connection, String database, String table) {
        String sql = "SELECT c.reltuples::bigint AS estimated_rows, "
            + "pg_catalog.pg_relation_size(c.oid) AS data_bytes, "
            + "pg_catalog.pg_indexes_size(c.oid) AS index_bytes, "
            + "obj_description(c.oid, 'pg_class') AS comment "
            + "FROM pg_catalog.pg_class c "
            + "JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace "
            + "WHERE n.nspname = ? AND c.relname = ? AND c.relkind IN ('r','p')";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, database);
            statement.setString(2, table);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) return null;
                Long rows = rs.getLong("estimated_rows");
                if (rs.wasNull()) rows = null;
                Long data = rs.getLong("data_bytes");
                if (rs.wasNull()) data = null;
                Long indexes = rs.getLong("index_bytes");
                if (rs.wasNull()) indexes = null;
                return new TableStats(EngineId.POSTGRESQL, rows, data, indexes, null, null, null, rs.getString("comment"));
            }
        } catch (SQLException ignored) {
            return null;
        }
    }

    private String escapeLike(String value) {
        return value.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    private String jdbcTypeNameFromPg(String dataType) {
        if (dataType == null) return "OTHER";
        String t = dataType.toLowerCase(Locale.ROOT);
        if ("int8".equals(t) || "bigint".equals(t)) return "BIGINT";
        if ("int4".equals(t) || "int".equals(t) || "integer".equals(t)) return "INTEGER";
        if ("int2".equals(t) || "smallint".equals(t) || "smallserial".equals(t)) return "SMALLINT";
        if ("serial".equals(t) || "serial4".equals(t)) return "INTEGER";
        if ("bigserial".equals(t) || "serial8".equals(t)) return "BIGINT";
        if ("numeric".equals(t) || "decimal".equals(t)) return "DECIMAL";
        if ("float4".equals(t) || "real".equals(t)) return "REAL";
        if ("float8".equals(t) || "double precision".equals(t)) return "DOUBLE";
        if ("bool".equals(t) || "boolean".equals(t)) return "BOOLEAN";
        if ("varchar".equals(t) || "character varying".equals(t)) return "VARCHAR";
        if ("bpchar".equals(t) || "char".equals(t) || "character".equals(t)) return "CHAR";
        if ("text".equals(t)) return "LONGVARCHAR";
        if ("bytea".equals(t)) return "BINARY";
        if ("date".equals(t)) return "DATE";
        if ("time".equals(t) || "timetz".equals(t)) return "TIME";
        if ("timestamp".equals(t) || "timestamptz".equals(t)) return "TIMESTAMP";
        if ("json".equals(t) || "jsonb".equals(t) || "uuid".equals(t)) return "VARCHAR";
        return "OTHER";
    }

    private boolean system(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        return "information_schema".equals(n) || n.startsWith("pg_");
    }
    private String jdbcTypeName(int type) {
        String name = JDBC_NAMES.get(type);
        return name == null ? "OTHER" : name;
    }
    private String normalizeTableType(String value) {
        return value != null && value.toUpperCase(Locale.ROOT).contains("VIEW") ? "VIEW" : "TABLE";
    }
    private String indexType(short value) {
        switch (value) {
            case DatabaseMetaData.tableIndexClustered: return "CLUSTERED";
            case DatabaseMetaData.tableIndexHashed: return "HASHED";
            case DatabaseMetaData.tableIndexStatistic: return "STATISTIC";
            default: return "OTHER";
        }
    }
    private Integer integer(ResultSet rs, String name) throws SQLException {
        int value = rs.getInt(name);
        return rs.wasNull() ? null : value;
    }
    private boolean contains(String value, String keyword) {
        return keyword == null || keyword.isEmpty() || value.toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT));
    }
    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static class IndexAccumulator {
        final String name, type;
        final boolean unique;
        final Map<Short, String> columns = new TreeMap<Short, String>();
        IndexAccumulator(String name, boolean unique, String type) {
            this.name = name;
            this.unique = unique;
            this.type = type;
        }
    }
}
