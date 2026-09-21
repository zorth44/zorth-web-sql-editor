package com.bocsoft.sqleditor.metadata;

import com.bocsoft.sqleditor.auth.AuthContext;
import com.bocsoft.sqleditor.common.ApiException;
import com.bocsoft.sqleditor.common.SqlEditorMetrics;
import com.bocsoft.sqleditor.config.SqlEditorProperties;
import com.bocsoft.sqleditor.datasource.SavedDataSource;
import com.bocsoft.sqleditor.datasource.TargetConnectionProvider;
import com.bocsoft.sqleditor.datasource.api.CursorPage;
import com.bocsoft.sqleditor.datasource.connection.ConnectionUse;
import com.bocsoft.sqleditor.engine.ConstraintGrouping;
import com.bocsoft.sqleditor.engine.EngineSupport;
import com.bocsoft.sqleditor.metadata.api.ColumnSearchItem;
import com.bocsoft.sqleditor.metadata.api.DatabaseItem;
import com.bocsoft.sqleditor.metadata.api.IndexItem;
import com.bocsoft.sqleditor.metadata.api.MetadataCoverage;
import com.bocsoft.sqleditor.metadata.api.MetadataSection;
import com.bocsoft.sqleditor.metadata.api.RelationshipDirection;
import com.bocsoft.sqleditor.metadata.api.RelationshipEdge;
import com.bocsoft.sqleditor.metadata.api.RelationshipPage;
import com.bocsoft.sqleditor.metadata.api.TableConstraintLimits;
import com.bocsoft.sqleditor.metadata.api.TableConstraintMetadata;
import com.bocsoft.sqleditor.metadata.api.TableDetailResponse;
import com.bocsoft.sqleditor.metadata.api.TableItem;
import com.bocsoft.sqleditor.metadata.api.TableMetadata;
import com.bocsoft.sqleditor.metadata.api.UniqueKeyItem;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class MetadataService {
    private static final Logger LOG = LoggerFactory.getLogger(MetadataService.class);
    private final TargetConnectionProvider targets;
    private final MetadataCursorCodec cursors;
    private final RelationshipCursorCodec relationshipCursors;
    private final SqlEditorProperties properties;
    private final SqlEditorMetrics metrics;

    public MetadataService(TargetConnectionProvider targets, MetadataCursorCodec cursors,
                           RelationshipCursorCodec relationshipCursors, SqlEditorProperties properties,
                           SqlEditorMetrics metrics) {
        this.targets = targets;
        this.cursors = cursors;
        this.relationshipCursors = relationshipCursors;
        this.properties = properties;
        this.metrics = metrics;
    }

    public CursorPage<DatabaseItem> databases(AuthContext auth, String id, String keyword, int pageSize, String token, boolean includeSystem) {
        if (pageSize < 1 || pageSize > 200) throw ApiException.validation("pageSize", "OUT_OF_RANGE", "每页数量必须在 1 到 200 之间");
        String q = normalize(keyword);
        String scope = id + "|db|" + q + "|" + includeSystem + "|" + pageSize;
        String after = cursors.decode(token, scope);
        SavedDataSource source = targets.require(auth, id);
        EngineSupport engine = targets.engine(source);
        List<DatabaseItem> all = jdbc(source, engine, c -> engine.listDatabases(c, q, includeSystem), false);
        return page(all, pageSize, after, scope, item -> item.getName());
    }

    public CursorPage<TableItem> tables(AuthContext auth, String id, String database, String keyword, String types, int pageSize, String token) {
        SavedDataSource source = targets.require(auth, id);
        EngineSupport engine = targets.engine(source);
        engine.validateIdentifier("database", database);
        if (pageSize < 1 || pageSize > 200) throw ApiException.validation("pageSize", "OUT_OF_RANGE", "每页数量必须在 1 到 200 之间");
        String q = normalize(keyword);
        String[] accepted = parseTypes(types);
        String scope = id + "|tables|" + database + "|" + q + "|" + String.join(",", accepted) + "|" + pageSize;
        String after = cursors.decode(token, scope);
        List<TableItem> all = jdbc(source, engine, c -> engine.listTables(c, database, q, accepted), false);
        return page(all, pageSize, after, scope, item -> item.getName());
    }

    public TableDetailResponse detail(AuthContext auth, String id, String database, String table) {
        SavedDataSource source = targets.require(auth, id);
        EngineSupport engine = targets.engine(source);
        engine.validateIdentifier("database", database);
        engine.validateIdentifier("table", table);
        return jdbc(source, engine, c -> engine.tableDetail(c, database, table), false);
    }

    public TableMetadata enrichedDetail(AuthContext auth, String id, String database, String table) {
        long started = System.nanoTime();
        String status = "success";
        int count = 0;
        SavedDataSource source = null;
        try {
            source = targets.require(auth, id);
            EngineSupport engine = targets.engine(source);
            engine.validateIdentifier("database", database);
            engine.validateIdentifier("table", table);
            TableConstraintLimits limits = constraintLimits();
            TableMetadata metadata = jdbc(source, engine, c -> {
                TableDetailResponse detail = engine.tableDetail(c, database, table);
                TableConstraintMetadata constraints = engine.tableConstraints(c, database, table, limits);
                return new TableMetadata(detail, withIndexFallback(detail, constraints, limits));
            }, true);
            count = metadata.getDetail().getColumns() == null ? 0 : metadata.getDetail().getColumns().size();
            return metadata;
        } catch (ApiException exception) {
            status = outcome(exception);
            throw exception;
        } finally {
            record("detail", source == null ? id : source.getId(), count, started, status);
        }
    }

    public RelationshipPage relationships(AuthContext auth, String id, String database, String table, String direction,
                                          int pageSize, String token) {
        long started = System.nanoTime();
        String status = "success";
        int count = 0;
        SavedDataSource source = null;
        try {
            source = targets.require(auth, id);
            EngineSupport engine = targets.engine(source);
            if (!engine.supportsRelationshipMetadata()) throw EngineSupport.capabilityNotSupported();
            engine.validateIdentifier("database", database);
            engine.validateIdentifier("table", table);
            String resolvedDirection = direction(direction);
            int maxPage = properties.getAgentApi().getMaxRelationshipsPerPage();
            if (pageSize < 1 || pageSize > maxPage) {
                throw ApiException.validation("pageSize", "OUT_OF_RANGE", "每页数量必须在 1 到 " + maxPage + " 之间");
            }
            RelationshipCursorCodec.Scope scope = new RelationshipCursorCodec.Scope(
                id, auth.getProductId(), database, table, resolvedDirection, pageSize);
            String after = relationshipCursors.decode(token, scope);
            final int collectionCap = relationshipCollectionCap();
            final TableConstraintLimits limits = constraintLimits();
            List<RelationshipEdge> collected = jdbc(source, engine, c -> {
                TableDetailResponse detail = engine.tableDetail(c, database, table);
                TableConstraintMetadata constraints = engine.tableConstraints(c, database, table, limits);
                Set<String> uniqueSets = ConstraintGrouping.uniqueColumnSets(
                    detail.getPrimaryKey() == null ? Collections.<String>emptyList() : detail.getPrimaryKey().getColumns(),
                    constraints.getUniqueKeys().getItems());
                List<RelationshipEdge> outbound = Collections.emptyList();
                List<RelationshipEdge> inbound = Collections.emptyList();
                if (RelationshipDirection.BOTH.equals(resolvedDirection) || RelationshipDirection.OUTBOUND.equals(resolvedDirection)) {
                    outbound = engine.importedRelationships(c, database, table, uniqueSets, collectionCap);
                }
                if (RelationshipDirection.BOTH.equals(resolvedDirection) || RelationshipDirection.INBOUND.equals(resolvedDirection)) {
                    inbound = engine.exportedRelationships(c, database, table, uniqueSets, collectionCap);
                }
                return ConstraintGrouping.mergeAndSort(outbound, inbound);
            }, true);
            boolean overflow = collected.size() > collectionCap;
            if (overflow) collected = new ArrayList<RelationshipEdge>(collected.subList(0, collectionCap));
            RelationshipPage page = pageRelationships(collected, pageSize, after, scope, overflow);
            count = page.getItems().size();
            return page;
        } catch (ApiException exception) {
            status = outcome(exception);
            throw exception;
        } finally {
            record("relationships", source == null ? id : source.getId(), count, started, status);
        }
    }

    public CursorPage<ColumnSearchItem> columns(AuthContext auth, String id, String database, String keyword, int pageSize, String token) {
        SavedDataSource source = targets.require(auth, id);
        EngineSupport engine = targets.engine(source);
        engine.validateIdentifier("database", database);
        if (pageSize < 1 || pageSize > 200) throw ApiException.validation("pageSize", "OUT_OF_RANGE", "每页数量必须在 1 到 200 之间");
        String q = normalize(keyword);
        if (q.isEmpty()) throw ApiException.validation("keyword", "REQUIRED", "关键词不能为空");
        String scope = id + "|columns|" + database + "|" + q + "|" + pageSize;
        String after = cursors.decode(token, scope);
        List<ColumnSearchItem> all = jdbc(source, engine, c -> engine.searchColumns(c, database, q), false);
        return page(all, pageSize, after, scope, item -> item.cursorKey());
    }

    private TableConstraintMetadata withIndexFallback(TableDetailResponse detail, TableConstraintMetadata constraints,
                                                      TableConstraintLimits limits) {
        if (constraints == null) return TableConstraintMetadata.unavailable();
        MetadataSection<IndexItem> indexes = constraints.getIndexes();
        if (indexes == null || indexes.isUnavailable()) {
            indexes = ConstraintGrouping.cropIndexes(detail.getIndexes(), limits.getMaxIndexes());
        }
        MetadataSection<UniqueKeyItem> uniqueKeys = constraints.getUniqueKeys() == null
            ? MetadataSection.<UniqueKeyItem>unavailable() : constraints.getUniqueKeys();
        return new TableConstraintMetadata(uniqueKeys, constraints.getForeignKeys(), indexes);
    }

    private RelationshipPage pageRelationships(List<RelationshipEdge> all, int size, String after,
                                               RelationshipCursorCodec.Scope scope, boolean overflow) {
        int start = 0;
        if (after != null) {
            while (start < all.size() && all.get(start).getSortKey().compareToIgnoreCase(after) <= 0) start++;
        }
        int end = Math.min(all.size(), start + size);
        List<RelationshipEdge> items = new ArrayList<RelationshipEdge>(all.subList(start, end));
        boolean more = end < all.size();
        String next = more && !items.isEmpty()
            ? relationshipCursors.encode(items.get(items.size() - 1).getSortKey(), scope) : null;
        String coverage = more || overflow ? MetadataCoverage.TRUNCATED : MetadataCoverage.COMPLETE;
        return new RelationshipPage(items, next, coverage, Integer.valueOf(size));
    }

    private TableConstraintLimits constraintLimits() {
        SqlEditorProperties.AgentApi agent = properties.getAgentApi();
        return new TableConstraintLimits(agent.getMaxUniqueKeys(), agent.getMaxForeignKeys(), agent.getMaxIndexes());
    }

    private int relationshipCollectionCap() {
        return Math.min(1000, Math.max(100, properties.getAgentApi().getMaxRelationshipsPerPage() * 10));
    }

    private String direction(String raw) {
        String value = raw == null || raw.trim().isEmpty() ? RelationshipDirection.BOTH : raw.trim().toUpperCase(Locale.ROOT);
        if (RelationshipDirection.BOTH.equals(value) || RelationshipDirection.OUTBOUND.equals(value)
            || RelationshipDirection.INBOUND.equals(value)) {
            return value;
        }
        throw ApiException.validation("direction", "INVALID", "仅支持 BOTH、OUTBOUND 和 INBOUND");
    }

    private <T> T jdbc(SavedDataSource source, EngineSupport engine, ConnectionUse.Work<T> work, boolean redactVendor) {
        try {
            return ConnectionUse.execute(targets.borrow(source), source.getDefaultDatabase(), engine, targets.evictor(source), work);
        } catch (ApiException e) {
            throw e;
        } catch (SQLException e) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "METADATA_QUERY_FAILED",
                redactVendor ? "元数据查询失败" : safe(e));
        }
    }

    private <T> CursorPage<T> page(List<T> all, int size, String after, String scope, Name<T> name) {
        int start = 0;
        if (after != null) {
            while (start < all.size() && name.get(all.get(start)).compareToIgnoreCase(after) <= 0) start++;
        }
        int end = Math.min(all.size(), start + size);
        List<T> items = new ArrayList<T>(all.subList(start, end));
        String next = end < all.size() && !items.isEmpty() ? cursors.encode(name.get(items.get(items.size() - 1)), scope) : null;
        return new CursorPage<T>(items, next);
    }

    private String[] parseTypes(String raw) {
        String v = raw == null ? "TABLE,VIEW" : raw;
        LinkedHashSet<String> out = new LinkedHashSet<String>();
        for (String x : v.split(",")) {
            String t = x.trim().toUpperCase(Locale.ROOT);
            if ("TABLE".equals(t)) out.add("TABLE");
            else if ("VIEW".equals(t)) out.add("VIEW");
            else throw ApiException.validation("types", "INVALID", "仅支持 TABLE 和 VIEW");
        }
        if (out.isEmpty()) throw ApiException.validation("types", "REQUIRED", "至少选择一种对象类型");
        return out.toArray(new String[0]);
    }

    private String normalize(String v) {
        String q = v == null ? "" : v.trim();
        if (q.length() > 200) throw ApiException.validation("keyword", "OUT_OF_RANGE", "关键词最多 200 个字符");
        return q;
    }

    private String safe(SQLException e) {
        String m = e.getMessage();
        return m == null ? "元数据查询失败" : (m.length() > 500 ? m.substring(0, 500) : m);
    }

    private void record(String operation, String datasourceId, int count, long started, String status) {
        long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
        metrics.metadata(operation, status, count, durationMs);
        LOG.info("metadata requestId={} datasource={} operation={} count={} durationMs={} status={}",
            MDC.get("requestId"), datasourceId, operation, count, durationMs, status);
    }

    private String outcome(ApiException exception) {
        if (exception.getStatus() == HttpStatus.NOT_FOUND) return "not_found";
        if ("CAPABILITY_NOT_SUPPORTED".equals(exception.getCode())) return "unsupported";
        if (exception.getStatus() == HttpStatus.BAD_REQUEST) return "invalid";
        return "failed";
    }

    private interface Name<T> { String get(T value); }
}
