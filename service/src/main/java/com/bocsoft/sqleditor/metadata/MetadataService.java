package com.bocsoft.sqleditor.metadata;

import com.bocsoft.sqleditor.auth.AuthContext;
import com.bocsoft.sqleditor.common.ApiException;
import com.bocsoft.sqleditor.datasource.SavedDataSource;
import com.bocsoft.sqleditor.datasource.TargetConnectionProvider;
import com.bocsoft.sqleditor.datasource.api.CursorPage;
import com.bocsoft.sqleditor.datasource.connection.ConnectionUse;
import com.bocsoft.sqleditor.engine.EngineSupport;
import com.bocsoft.sqleditor.metadata.api.DatabaseItem;
import com.bocsoft.sqleditor.metadata.api.TableDetailResponse;
import com.bocsoft.sqleditor.metadata.api.TableItem;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class MetadataService {
    private final TargetConnectionProvider targets;
    private final MetadataCursorCodec cursors;
    public MetadataService(TargetConnectionProvider targets, MetadataCursorCodec cursors) {
        this.targets = targets;
        this.cursors = cursors;
    }

    public CursorPage<DatabaseItem> databases(AuthContext auth, String id, String keyword, int pageSize, String token, boolean includeSystem) {
        if (pageSize < 1 || pageSize > 200) throw ApiException.validation("pageSize", "OUT_OF_RANGE", "每页数量必须在 1 到 200 之间");
        String q = normalize(keyword);
        String scope = id + "|db|" + q + "|" + includeSystem + "|" + pageSize;
        String after = cursors.decode(token, scope);
        SavedDataSource source = targets.require(auth, id);
        EngineSupport engine = targets.engine(source);
        List<DatabaseItem> all = jdbc(source, engine, c -> engine.listDatabases(c, q, includeSystem));
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
        List<TableItem> all = jdbc(source, engine, c -> engine.listTables(c, database, q, accepted));
        return page(all, pageSize, after, scope, item -> item.getName());
    }

    public TableDetailResponse detail(AuthContext auth, String id, String database, String table) {
        SavedDataSource source = targets.require(auth, id);
        EngineSupport engine = targets.engine(source);
        engine.validateIdentifier("database", database);
        engine.validateIdentifier("table", table);
        return jdbc(source, engine, c -> engine.tableDetail(c, database, table));
    }

    private <T> T jdbc(SavedDataSource source, EngineSupport engine, ConnectionUse.Work<T> work) {
        try {
            return ConnectionUse.execute(targets.borrow(source), source.getDefaultDatabase(), engine, targets.evictor(source), work);
        } catch (ApiException e) {
            throw e;
        } catch (SQLException e) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "METADATA_QUERY_FAILED", safe(e));
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
    private interface Name<T> { String get(T value); }
}
