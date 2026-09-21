package com.bocsoft.sqleditor.engine;

import com.bocsoft.sqleditor.metadata.api.ConstraintColumns;
import com.bocsoft.sqleditor.metadata.api.ConstraintEvidence;
import com.bocsoft.sqleditor.metadata.api.ForeignKeyItem;
import com.bocsoft.sqleditor.metadata.api.IndexItem;
import com.bocsoft.sqleditor.metadata.api.MetadataSection;
import com.bocsoft.sqleditor.metadata.api.RelationshipCardinality;
import com.bocsoft.sqleditor.metadata.api.RelationshipDirection;
import com.bocsoft.sqleditor.metadata.api.RelationshipEdge;
import com.bocsoft.sqleditor.metadata.api.UniqueKeyItem;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public final class ConstraintGrouping {
    private ConstraintGrouping() { }

    public static MetadataSection<IndexItem> cropIndexes(List<IndexItem> indexes, int limit) {
        return crop(indexes, limit);
    }

    public static MetadataSection<UniqueKeyItem> uniqueKeys(List<IndexSnapshot> indexes, List<String> primaryKeyColumns,
                                                            String primaryKeyName, Set<String> uniqueConstraintNames,
                                                            int limit) {
        Set<String> pkColumns = lowerSet(primaryKeyColumns);
        String pkName = normalize(primaryKeyName);
        Set<String> proven = lowerSet(uniqueConstraintNames);
        List<UniqueKeyItem> out = new ArrayList<UniqueKeyItem>();
        Set<String> seen = new HashSet<String>();
        for (IndexSnapshot index : indexes) {
            if (index == null || !index.unique || "STATISTIC".equals(index.type) || !ConstraintColumns.nonempty(index.columns)) {
                continue;
            }
            if (isPrimaryKeyIndex(index, pkName, pkColumns)) continue;
            String setKey = ConstraintColumns.setKey(index.columns);
            if (!seen.add(setKey)) continue;
            String evidence = proven.contains(normalize(index.name))
                ? ConstraintEvidence.UNIQUE_CONSTRAINT
                : ConstraintEvidence.UNIQUE_INDEX;
            String name = hasText(index.name) ? index.name : null;
            out.add(new UniqueKeyItem(name, index.columns, evidence));
        }
        return crop(out, limit);
    }

    public static Set<String> uniqueColumnSets(List<String> primaryKeyColumns, List<UniqueKeyItem> uniqueKeys) {
        Set<String> out = new HashSet<String>();
        if (ConstraintColumns.nonempty(primaryKeyColumns)) out.add(ConstraintColumns.setKey(primaryKeyColumns));
        if (uniqueKeys != null) {
            for (UniqueKeyItem key : uniqueKeys) out.add(ConstraintColumns.setKey(key.getColumns()));
        }
        return out;
    }

    public static List<RelationshipEdge> groupForeignKeys(List<ForeignKeySequenceRow> rows, String requestedNamespace,
                                                          String requestedTable, String direction,
                                                          Set<String> requestedUniqueSets, int limit) {
        Map<String, ForeignKeyAccumulator> grouped = new LinkedHashMap<String, ForeignKeyAccumulator>();
        String lastAnonymousKey = null;
        int anonymous = 0;
        if (rows != null) {
            for (ForeignKeySequenceRow row : rows) {
                if (row == null || !hasText(row.fkColumn) || !hasText(row.pkColumn) || !hasText(row.fkTable) || !hasText(row.pkTable)) {
                    continue;
                }
                String key;
                if (hasText(row.constraintName)) {
                    key = "n|" + row.constraintName + "|" + ns(row.fkNamespace) + "|" + row.fkTable + "|"
                        + ns(row.pkNamespace) + "|" + row.pkTable;
                    lastAnonymousKey = null;
                } else {
                    if (row.keySeq <= 1 || lastAnonymousKey == null) {
                        anonymous++;
                        lastAnonymousKey = "a|" + ns(row.fkNamespace) + "|" + row.fkTable + "|" + ns(row.pkNamespace)
                            + "|" + row.pkTable + "|" + row.fkColumn + "|" + row.pkColumn + "|" + anonymous;
                    }
                    key = lastAnonymousKey;
                }
                ForeignKeyAccumulator accumulator = grouped.get(key);
                if (accumulator == null) {
                    accumulator = new ForeignKeyAccumulator(hasText(row.constraintName) ? row.constraintName : null,
                        ns(row.fkNamespace), row.fkTable, ns(row.pkNamespace), row.pkTable);
                    grouped.put(key, accumulator);
                }
                accumulator.source.put(Short.valueOf(row.keySeq), row.fkColumn);
                accumulator.target.put(Short.valueOf(row.keySeq), row.pkColumn);
            }
        }
        List<RelationshipEdge> edges = new ArrayList<RelationshipEdge>();
        for (ForeignKeyAccumulator accumulator : grouped.values()) {
            List<String> source = new ArrayList<String>(accumulator.source.values());
            List<String> target = new ArrayList<String>(accumulator.target.values());
            if (!ConstraintColumns.validPair(source, target)) continue;
            if (RelationshipDirection.OUTBOUND.equals(direction)
                && (hasText(requestedTable) && !requestedTable.equals(accumulator.sourceTable))) continue;
            if (RelationshipDirection.INBOUND.equals(direction)
                && (hasText(requestedTable) && !requestedTable.equals(accumulator.targetTable))) continue;
            String cardinality = cardinality(direction, source, requestedUniqueSets);
            edges.add(new RelationshipEdge(
                accumulator.sourceNamespace, accumulator.sourceTable, source,
                accumulator.targetNamespace, accumulator.targetTable, target,
                direction, accumulator.constraintName, ConstraintEvidence.FOREIGN_KEY, cardinality));
        }
        edges.sort(Comparator.comparing(RelationshipEdge::getSortKey, String.CASE_INSENSITIVE_ORDER));
        if (edges.size() > limit) return new ArrayList<RelationshipEdge>(edges.subList(0, limit + 1));
        return edges;
    }

    public static List<ForeignKeyItem> outboundForeignKeys(List<RelationshipEdge> outbound, int limit) {
        List<ForeignKeyItem> out = new ArrayList<ForeignKeyItem>();
        if (outbound == null) return out;
        for (RelationshipEdge edge : outbound) {
            out.add(new ForeignKeyItem(edge.getConstraintName(), edge.getSourceColumns(), edge.getTargetDatabase(),
                edge.getTargetTable(), edge.getTargetColumns(), edge.getEvidence()));
        }
        return out;
    }

    public static <T> MetadataSection<T> crop(List<T> items, int limit) {
        List<T> source = items == null ? Collections.<T>emptyList() : items;
        int cap = Math.max(1, limit);
        if (source.size() > cap) {
            return MetadataSection.truncated(new ArrayList<T>(source.subList(0, cap)), cap);
        }
        return MetadataSection.complete(source, cap);
    }

    public static List<RelationshipEdge> mergeAndSort(List<RelationshipEdge> outbound, List<RelationshipEdge> inbound) {
        List<RelationshipEdge> out = new ArrayList<RelationshipEdge>();
        if (outbound != null) out.addAll(outbound);
        if (inbound != null) out.addAll(inbound);
        out.sort(Comparator.comparing(RelationshipEdge::getSortKey, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    private static String cardinality(String direction, List<String> sourceColumns, Set<String> requestedUniqueSets) {
        boolean sourceUnique = requestedUniqueSets != null && requestedUniqueSets.contains(ConstraintColumns.setKey(sourceColumns));
        if (RelationshipDirection.OUTBOUND.equals(direction)) {
            return sourceUnique ? RelationshipCardinality.ONE_TO_ONE : RelationshipCardinality.MANY_TO_ONE;
        }
        if (RelationshipDirection.INBOUND.equals(direction)) {
            return RelationshipCardinality.ONE_TO_MANY;
        }
        return null;
    }

    private static boolean isPrimaryKeyIndex(IndexSnapshot index, String pkName, Set<String> pkColumns) {
        if (hasText(pkName) && pkName.equals(normalize(index.name))) return true;
        return !pkColumns.isEmpty() && pkColumns.equals(lowerSet(index.columns));
    }

    private static Set<String> lowerSet(Iterable<String> values) {
        Set<String> out = new HashSet<String>();
        if (values == null) return out;
        for (String value : values) {
            if (hasText(value)) out.add(value.toLowerCase(Locale.ROOT));
        }
        return out;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private static String ns(String value) {
        return value == null ? "" : value;
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    public static final class IndexSnapshot {
        public final String name;
        public final boolean unique;
        public final String type;
        public final List<String> columns;

        public IndexSnapshot(String name, boolean unique, String type, List<String> columns) {
            this.name = name;
            this.unique = unique;
            this.type = type;
            this.columns = columns == null ? Collections.<String>emptyList() : columns;
        }
    }

    public static final class ForeignKeySequenceRow {
        public final String constraintName;
        public final String fkNamespace;
        public final String fkTable;
        public final String fkColumn;
        public final String pkNamespace;
        public final String pkTable;
        public final String pkColumn;
        public final short keySeq;

        public ForeignKeySequenceRow(String constraintName, String fkNamespace, String fkTable, String fkColumn,
                                     String pkNamespace, String pkTable, String pkColumn, short keySeq) {
            this.constraintName = constraintName;
            this.fkNamespace = fkNamespace;
            this.fkTable = fkTable;
            this.fkColumn = fkColumn;
            this.pkNamespace = pkNamespace;
            this.pkTable = pkTable;
            this.pkColumn = pkColumn;
            this.keySeq = keySeq;
        }
    }

    private static final class ForeignKeyAccumulator {
        final String constraintName;
        final String sourceNamespace;
        final String sourceTable;
        final String targetNamespace;
        final String targetTable;
        final Map<Short, String> source = new TreeMap<Short, String>();
        final Map<Short, String> target = new TreeMap<Short, String>();

        ForeignKeyAccumulator(String constraintName, String sourceNamespace, String sourceTable,
                              String targetNamespace, String targetTable) {
            this.constraintName = constraintName;
            this.sourceNamespace = sourceNamespace;
            this.sourceTable = sourceTable;
            this.targetNamespace = targetNamespace;
            this.targetTable = targetTable;
        }
    }
}
