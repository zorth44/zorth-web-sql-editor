package com.bocsoft.sqleditor.engine.hive;

import com.bocsoft.sqleditor.common.ApiException;
import com.bocsoft.sqleditor.metadata.api.ColumnItem;
import com.bocsoft.sqleditor.metadata.api.DatabaseItem;
import com.bocsoft.sqleditor.metadata.api.IndexItem;
import com.bocsoft.sqleditor.metadata.api.TableDetailResponse;
import com.bocsoft.sqleditor.metadata.api.TableItem;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpStatus;

final class HiveCatalogs {
    private static final Set<String> SYSTEM = new HashSet<String>(Arrays.asList("information_schema"));

    void validateIdentifier(String field, String value) {
        if (value == null || value.trim().isEmpty() || value.length() > 128
            || value.indexOf('\0') >= 0 || value.indexOf(';') >= 0) {
            throw ApiException.validation(field, "INVALID", "数据库标识符不合法");
        }
    }

    List<DatabaseItem> listDatabases(Connection connection, String keyword, boolean includeSystem) throws SQLException {
        List<DatabaseItem> out = new ArrayList<DatabaseItem>();
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("show databases")) {
            while (rs.next()) {
                String name = rs.getString(1);
                if (name == null) continue;
                if ((includeSystem || !SYSTEM.contains(name.toLowerCase(Locale.ROOT))) && contains(name, keyword)) {
                    out.add(new DatabaseItem(name));
                }
            }
        }
        out.sort(Comparator.comparing(DatabaseItem::getName, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    List<TableItem> listTables(Connection connection, String database, String keyword, String[] types) throws SQLException {
        ensureNamespace(connection, database);
        boolean onlyViews = onlyViews(types);
        List<TableItem> out = new ArrayList<TableItem>();
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(onlyViews ? "show views" : "show tables")) {
            while (rs.next()) {
                String name = rs.getString(1);
                if (name != null && contains(name, keyword)) {
                    out.add(new TableItem(database, name, onlyViews ? "VIEW" : "TABLE", null));
                }
            }
        }
        out.sort(Comparator.comparing(TableItem::getName, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    TableDetailResponse tableDetail(Connection connection, String database, String table) throws SQLException {
        ensureNamespace(connection, database);
        List<ColumnItem> columns = readColumns(connection, table);
        if (columns.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "TABLE_NOT_FOUND", "表不存在或已不可见");
        return new TableDetailResponse(database, table, columns, null, Collections.<IndexItem>emptyList(), readDdl(connection, table));
    }

    void ensureNamespace(Connection connection, String database) throws SQLException {
        if (!hasText(database)) return;
        try (Statement statement = connection.createStatement()) {
            statement.execute("use " + quoteIdentifier(database));
        } catch (SQLException failure) {
            throw new ApiException(HttpStatus.NOT_FOUND, "DATABASE_NOT_FOUND", "数据库不存在或已不可见");
        }
    }

    void applyNamespace(Connection connection, String namespace) throws SQLException {
        if (!hasText(namespace)) return;
        try (Statement statement = connection.createStatement()) {
            statement.execute("use " + quoteIdentifier(namespace));
        }
    }

    boolean restoreSession(Connection connection, String defaultNamespace) throws SQLException {
        if (hasText(defaultNamespace)) {
            applyNamespace(connection, defaultNamespace);
            return false;
        }
        String current = currentDatabase(connection);
        return hasText(current) && !"default".equalsIgnoreCase(current);
    }

    void verifyDefaultNamespace(Connection connection, String defaultNamespace) throws SQLException {
        if (!hasText(defaultNamespace)) return;
        try {
            applyNamespace(connection, defaultNamespace);
        } catch (Throwable failure) {
            throw new SQLException("Default database unavailable", "42000", 10072, failure);
        }
    }

    String quoteIdentifier(String value) {
        return "`" + value.replace("`", "``") + "`";
    }

    private List<ColumnItem> readColumns(Connection connection, String table) throws SQLException {
        List<ColumnItem> columns = new ArrayList<ColumnItem>();
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("desc " + quoteIdentifier(table))) {
            int ordinal = 1;
            while (rs.next()) {
                String name = rs.getString(1);
                if (name == null) continue;
                String trimmed = name.trim();
                if (trimmed.isEmpty()) continue;
                if (trimmed.charAt(0) == '#') break;
                String typeName = rs.getString(2);
                columns.add(new ColumnItem(
                    trimmed, typeName, jdbcTypeName(typeName), length(typeName), precision(typeName), scale(typeName),
                    true, null, null, rs.getString(3), Integer.valueOf(ordinal++), false));
            }
        }
        return columns;
    }

    private String readDdl(Connection connection, String table) {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("show create table " + quoteIdentifier(table))) {
            if (rs.next()) return rs.getString(2);
        } catch (SQLException ignored) {
        }
        return null;
    }

    private String currentDatabase(Connection connection) {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("select current_database()")) {
            return rs.next() ? rs.getString(1) : null;
        } catch (SQLException ignored) {
            return null;
        }
    }

    private boolean onlyViews(String[] types) {
        if (types == null || types.length == 0) return false;
        for (String type : types) {
            if (type == null || !"VIEW".equalsIgnoreCase(type.trim())) return false;
        }
        return true;
    }

    private String jdbcTypeName(String typeName) {
        if (typeName == null) return "OTHER";
        String base = baseType(typeName);
        if ("bigint".equals(base)) return "BIGINT";
        if ("int".equals(base) || "integer".equals(base)) return "INTEGER";
        if ("smallint".equals(base)) return "SMALLINT";
        if ("tinyint".equals(base)) return "TINYINT";
        if ("decimal".equals(base) || "numeric".equals(base)) return "DECIMAL";
        if ("double".equals(base)) return "DOUBLE";
        if ("float".equals(base)) return "REAL";
        if ("real".equals(base)) return "REAL";
        if ("boolean".equals(base)) return "BOOLEAN";
        if ("char".equals(base) || "varchar".equals(base) || "string".equals(base)) return "VARCHAR";
        if ("binary".equals(base)) return "BINARY";
        if ("date".equals(base)) return "DATE";
        if ("timestamp".equals(base) || "timestamptz".equals(base)) return "TIMESTAMP";
        return "OTHER";
    }

    private String baseType(String typeName) {
        String value = typeName.trim().toLowerCase(Locale.ROOT);
        int paren = value.indexOf('(');
        return paren < 0 ? value : value.substring(0, paren).trim();
    }

    private Integer length(String typeName) {
        String base = baseType(typeName);
        if (!("char".equals(base) || "varchar".equals(base) || "binary".equals(base))) return null;
        String[] args = typeArguments(typeName);
        return args.length >= 1 ? toInteger(args[0]) : null;
    }

    private Integer precision(String typeName) {
        if (!"decimal".equals(baseType(typeName)) && !"numeric".equals(baseType(typeName))) return null;
        String[] args = typeArguments(typeName);
        return args.length >= 1 ? toInteger(args[0]) : null;
    }

    private Integer scale(String typeName) {
        if (!"decimal".equals(baseType(typeName)) && !"numeric".equals(baseType(typeName))) return null;
        String[] args = typeArguments(typeName);
        return args.length >= 2 ? toInteger(args[1]) : null;
    }

    private String[] typeArguments(String typeName) {
        int open = typeName.indexOf('(');
        int close = typeName.indexOf(')', open + 1);
        if (open < 0 || close < 0) return new String[0];
        String[] parts = typeName.substring(open + 1, close).split(",");
        for (int i = 0; i < parts.length; i++) parts[i] = parts[i].trim();
        return parts;
    }

    private Integer toInteger(String value) {
        try {
            return Integer.valueOf(Integer.parseInt(value.trim()));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private boolean contains(String value, String keyword) {
        return value != null && (keyword == null || keyword.isEmpty()
            || value.toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT)));
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
