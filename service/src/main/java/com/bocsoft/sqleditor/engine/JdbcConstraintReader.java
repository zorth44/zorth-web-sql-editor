package com.bocsoft.sqleditor.engine;

import com.bocsoft.sqleditor.metadata.api.ForeignKeyItem;
import com.bocsoft.sqleditor.metadata.api.IndexItem;
import com.bocsoft.sqleditor.metadata.api.MetadataSection;
import com.bocsoft.sqleditor.metadata.api.RelationshipDirection;
import com.bocsoft.sqleditor.metadata.api.RelationshipEdge;
import com.bocsoft.sqleditor.metadata.api.TableConstraintLimits;
import com.bocsoft.sqleditor.metadata.api.TableConstraintMetadata;
import com.bocsoft.sqleditor.metadata.api.UniqueKeyItem;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public final class JdbcConstraintReader {
    private JdbcConstraintReader() { }

    public static TableConstraintMetadata read(Connection connection, String catalog, String schema, String namespace,
                                               String table, TableConstraintLimits limits,
                                               Set<String> uniqueConstraintNames) throws SQLException {
        DatabaseMetaData meta = connection.getMetaData();
        List<String> pkColumns = primaryKeyColumns(meta, catalog, schema, table);
        String pkName = primaryKeyName(meta, catalog, schema, table);
        List<ConstraintGrouping.IndexSnapshot> snapshots = new ArrayList<ConstraintGrouping.IndexSnapshot>();
        List<IndexItem> indexes = new ArrayList<IndexItem>();
        try (ResultSet rs = meta.getIndexInfo(catalog, schema, table, false, false)) {
            Map<String, IndexAccumulator> grouped = new LinkedHashMap<String, IndexAccumulator>();
            while (rs.next()) {
                String name = rs.getString("INDEX_NAME");
                String column = rs.getString("COLUMN_NAME");
                if (name == null || column == null) continue;
                IndexAccumulator accumulator = grouped.get(name);
                if (accumulator == null) {
                    short type = 0;
                    try { type = rs.getShort("TYPE"); } catch (SQLException ignored) { }
                    accumulator = new IndexAccumulator(name, !rs.getBoolean("NON_UNIQUE"), indexType(type));
                    grouped.put(name, accumulator);
                }
                short position = 0;
                try { position = rs.getShort("ORDINAL_POSITION"); } catch (SQLException ignored) { }
                accumulator.columns.put(Short.valueOf(position), column);
            }
            for (IndexAccumulator accumulator : grouped.values()) {
                List<String> columns = new ArrayList<String>(accumulator.columns.values());
                snapshots.add(new ConstraintGrouping.IndexSnapshot(accumulator.name, accumulator.unique, accumulator.type, columns));
                indexes.add(new IndexItem(accumulator.name, accumulator.unique, accumulator.type, columns));
            }
        } catch (SQLFeatureNotSupportedException unsupported) {
            return TableConstraintMetadata.unavailable();
        }
        MetadataSection<UniqueKeyItem> uniqueKeys = ConstraintGrouping.uniqueKeys(
            snapshots, pkColumns, pkName, uniqueConstraintNames, limits.getMaxUniqueKeys());
        List<RelationshipEdge> outbound = imported(connection, catalog, schema, namespace, table,
            ConstraintGrouping.uniqueColumnSets(pkColumns, uniqueKeys.getItems()), limits.getMaxForeignKeys() + 1);
        boolean truncated = outbound.size() > limits.getMaxForeignKeys();
        if (truncated) outbound = new ArrayList<RelationshipEdge>(outbound.subList(0, limits.getMaxForeignKeys()));
        List<ForeignKeyItem> foreignKeys = ConstraintGrouping.outboundForeignKeys(outbound, limits.getMaxForeignKeys());
        MetadataSection<ForeignKeyItem> fkSection = truncated
            ? MetadataSection.truncated(foreignKeys, limits.getMaxForeignKeys())
            : MetadataSection.complete(foreignKeys, limits.getMaxForeignKeys());
        return new TableConstraintMetadata(uniqueKeys, fkSection, ConstraintGrouping.cropIndexes(indexes, limits.getMaxIndexes()));
    }

    public static List<RelationshipEdge> imported(Connection connection, String catalog, String schema, String namespace,
                                                  String table, Set<String> requestedUniqueSets, int limit) throws SQLException {
        return readKeys(connection, catalog, schema, namespace, table, requestedUniqueSets, limit, true);
    }

    public static List<RelationshipEdge> exported(Connection connection, String catalog, String schema, String namespace,
                                                  String table, Set<String> requestedUniqueSets, int limit) throws SQLException {
        return readKeys(connection, catalog, schema, namespace, table, requestedUniqueSets, limit, false);
    }

    private static List<RelationshipEdge> readKeys(Connection connection, String catalog, String schema, String namespace,
                                                   String table, Set<String> requestedUniqueSets, int limit,
                                                   boolean imported) throws SQLException {
        DatabaseMetaData meta = connection.getMetaData();
        List<ConstraintGrouping.ForeignKeySequenceRow> rows = new ArrayList<ConstraintGrouping.ForeignKeySequenceRow>();
        try (ResultSet rs = imported ? meta.getImportedKeys(catalog, schema, table) : meta.getExportedKeys(catalog, schema, table)) {
            while (rs.next()) {
                String fkNamespace = namespaceOf(rs, imported ? namespace : null, "FKTABLE_CAT", "FKTABLE_SCHEM", catalog != null);
                String pkNamespace = namespaceOf(rs, imported ? null : namespace, "PKTABLE_CAT", "PKTABLE_SCHEM", catalog != null);
                if (!hasText(fkNamespace)) fkNamespace = namespace;
                if (!hasText(pkNamespace)) pkNamespace = namespace;
                rows.add(new ConstraintGrouping.ForeignKeySequenceRow(
                    rs.getString("FK_NAME"),
                    fkNamespace,
                    rs.getString("FKTABLE_NAME"),
                    rs.getString("FKCOLUMN_NAME"),
                    pkNamespace,
                    rs.getString("PKTABLE_NAME"),
                    rs.getString("PKCOLUMN_NAME"),
                    rs.getShort("KEY_SEQ")));
            }
        } catch (SQLFeatureNotSupportedException unsupported) {
            return Collections.emptyList();
        }
        return ConstraintGrouping.groupForeignKeys(rows, namespace, table,
            imported ? RelationshipDirection.OUTBOUND : RelationshipDirection.INBOUND, requestedUniqueSets, Math.max(1, limit));
    }

    private static List<String> primaryKeyColumns(DatabaseMetaData meta, String catalog, String schema, String table) throws SQLException {
        Map<Short, String> order = new TreeMap<Short, String>();
        try (ResultSet rs = meta.getPrimaryKeys(catalog, schema, table)) {
            while (rs.next()) {
                String column = rs.getString("COLUMN_NAME");
                if (column == null) continue;
                order.put(Short.valueOf(rs.getShort("KEY_SEQ")), column);
            }
        } catch (SQLFeatureNotSupportedException ignored) {
            return Collections.emptyList();
        }
        return new ArrayList<String>(order.values());
    }

    private static String primaryKeyName(DatabaseMetaData meta, String catalog, String schema, String table) throws SQLException {
        try (ResultSet rs = meta.getPrimaryKeys(catalog, schema, table)) {
            while (rs.next()) {
                String name = rs.getString("PK_NAME");
                if (hasText(name)) return name;
            }
        } catch (SQLFeatureNotSupportedException ignored) { }
        return null;
    }

    private static String namespaceOf(ResultSet rs, String fallback, String catalogColumn, String schemaColumn, boolean useCatalog)
        throws SQLException {
        String value = useCatalog ? rs.getString(catalogColumn) : rs.getString(schemaColumn);
        if (hasText(value)) return value;
        String other = useCatalog ? rs.getString(schemaColumn) : rs.getString(catalogColumn);
        if (hasText(other)) return other;
        return fallback;
    }

    private static String indexType(short value) {
        switch (value) {
            case DatabaseMetaData.tableIndexClustered: return "CLUSTERED";
            case DatabaseMetaData.tableIndexHashed: return "HASHED";
            case DatabaseMetaData.tableIndexStatistic: return "STATISTIC";
            default: return "OTHER";
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static final class IndexAccumulator {
        final String name;
        final String type;
        final boolean unique;
        final Map<Short, String> columns = new TreeMap<Short, String>();
        IndexAccumulator(String name, boolean unique, String type) {
            this.name = name;
            this.unique = unique;
            this.type = type;
        }
    }
}
