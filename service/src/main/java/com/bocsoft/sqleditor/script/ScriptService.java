package com.bocsoft.sqleditor.script;

import com.bocsoft.sqleditor.auth.AuthContext;
import com.bocsoft.sqleditor.common.ApiException;
import com.bocsoft.sqleditor.config.SqlEditorProperties;
import com.bocsoft.sqleditor.datasource.DataSourceService;
import com.bocsoft.sqleditor.datasource.api.CursorPage;
import com.bocsoft.sqleditor.datasource.api.DataSourceDetailResponse;
import com.bocsoft.sqleditor.metadata.MetadataCursorCodec;
import com.bocsoft.sqleditor.script.api.ScriptDetail;
import com.bocsoft.sqleditor.script.api.ScriptSummary;
import com.bocsoft.sqleditor.script.api.ScriptWriteRequest;
import com.bocsoft.sqleditor.script.persistence.ScriptMapper;
import com.bocsoft.sqleditor.script.persistence.ScriptRecord;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ScriptService {
    private final ScriptMapper mapper;
    private final DataSourceService dataSources;
    private final MetadataCursorCodec cursors;
    private final SqlEditorProperties properties;
    private final Clock clock;

    public ScriptService(ScriptMapper mapper, DataSourceService dataSources, MetadataCursorCodec cursors,
                         SqlEditorProperties properties, Clock clock) {
        this.mapper = mapper;
        this.dataSources = dataSources;
        this.cursors = cursors;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public ScriptDetail create(AuthContext auth, ScriptWriteRequest request) {
        String name = requireName(request.getName());
        String statement = requireStatement(request.getStatement());
        int max = properties.getScripts().getMaxPerUser();
        if (mapper.countOwned(auth.getUserId()) >= max) {
            throw new ApiException(HttpStatus.CONFLICT, "SCRIPT_QUOTA_EXCEEDED", "已达到脚本数量上限");
        }
        Instant now = clock.instant();
        ScriptRecord record = new ScriptRecord();
        record.setId(UUID.randomUUID().toString());
        record.setUserId(auth.getUserId());
        record.setUsername(auth.getUsername());
        record.setProductId(auth.getProductId());
        record.setName(name);
        applyConnection(auth, record, request.getDataSourceId(), request.getDatabase());
        record.setStatementText(statement);
        record.setVersion(1);
        record.setCreatedBy(auth.getUserId());
        record.setCreatedByName(auth.getDisplayName());
        record.setCreatedAt(now);
        record.setUpdatedBy(auth.getUserId());
        record.setUpdatedByName(auth.getDisplayName());
        record.setUpdatedAt(now);
        mapper.insert(record);
        return detailOf(auth, record);
    }

    @Transactional(readOnly = true)
    public CursorPage<ScriptSummary> list(AuthContext auth, String keyword, String dataSourceId, String database,
                                          int pageSize, String pageToken) {
        String query = clean(keyword, 200, "keyword");
        if (pageSize < 1 || pageSize > 100) {
            throw ApiException.validation("pageSize", "OUT_OF_RANGE", "每页数量必须在 1 到 100 之间");
        }
        String source = blank(dataSourceId);
        String namespace = blank(database);
        String scope = "scripts|" + query + "|" + nulls(source) + "|" + nulls(namespace) + "|" + pageSize;
        String after = cursors.decode(pageToken, scope);
        Instant cursorTime = null;
        String cursorId = null;
        if (after != null) {
            int split = after.lastIndexOf('|');
            try {
                cursorTime = Instant.parse(after.substring(0, split));
                cursorId = after.substring(split + 1);
            } catch (Exception e) {
                throw ApiException.validation("pageToken", "INVALID", "分页游标无效");
            }
        }
        String pattern = query.isEmpty() ? null : "%" + escape(query) + "%";
        List<ScriptRecord> records = mapper.listOwned(auth.getUserId(), pattern, source, namespace,
            cursorTime, cursorId, pageSize + 1);
        boolean more = records.size() > pageSize;
        List<ScriptSummary> items = new ArrayList<ScriptSummary>();
        int size = Math.min(pageSize, records.size());
        for (int i = 0; i < size; i++) items.add(new ScriptSummary(records.get(i)));
        String next = null;
        if (more && !items.isEmpty()) {
            ScriptRecord last = records.get(size - 1);
            next = cursors.encode(last.getUpdatedAt().toString() + "|" + last.getId(), scope);
        }
        return new CursorPage<ScriptSummary>(items, next);
    }

    public ScriptDetail get(AuthContext auth, String id) {
        return detailOf(auth, requireOwned(auth, id));
    }

    @Transactional
    public ScriptDetail update(AuthContext auth, String id, ScriptWriteRequest request) {
        ScriptRecord current = requireOwned(auth, id);
        if (request.getVersion() == null) {
            throw ApiException.validation("version", "REQUIRED", "请提供当前版本");
        }
        if (current.getVersion() != request.getVersion()) throw conflict(current);
        current.setName(requireName(request.getName()));
        current.setStatementText(requireStatement(request.getStatement()));
        applyConnection(auth, current, request.getDataSourceId(), request.getDatabase());
        current.setUpdatedBy(auth.getUserId());
        current.setUpdatedByName(auth.getDisplayName());
        current.setUpdatedAt(clock.instant());
        current.setVersion(request.getVersion());
        if (mapper.updateCurrent(current) != 1) throw conflict(requireOwned(auth, id));
        return detailOf(auth, requireOwned(auth, id));
    }

    @Transactional
    public void delete(AuthContext auth, String id, long version) {
        ScriptRecord current = requireOwned(auth, id);
        if (mapper.deleteCurrent(id, auth.getUserId(), version) != 1) throw conflict(current);
    }

    private ScriptRecord requireOwned(AuthContext auth, String id) {
        ScriptRecord record = mapper.findOwned(id, auth.getUserId());
        if (record == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "SCRIPT_NOT_FOUND", "脚本不存在");
        }
        return record;
    }

    private ScriptDetail detailOf(AuthContext auth, ScriptRecord record) {
        boolean available = false;
        if (record.getDataSourceId() != null) {
            try {
                dataSources.get(auth, record.getDataSourceId());
                available = true;
            } catch (ApiException ignored) {
                available = false;
            }
        }
        return new ScriptDetail(record, available);
    }

    private void applyConnection(AuthContext auth, ScriptRecord record, String dataSourceId, String database) {
        String sourceId = blank(dataSourceId);
        if (sourceId == null) {
            record.setDataSourceId(null);
            record.setDataSourceName(null);
            record.setDatabaseName(null);
            return;
        }
        DataSourceDetailResponse source = dataSources.get(auth, sourceId);
        record.setDataSourceId(source.getId());
        record.setDataSourceName(source.getName());
        record.setDatabaseName(blank(database));
    }

    private String requireName(String value) {
        String name = value == null ? "" : value.trim();
        if (name.isEmpty() || name.length() > 100) {
            throw ApiException.validation("name", "OUT_OF_RANGE", "名称长度必须在 1 到 100 之间");
        }
        return name;
    }

    private String requireStatement(String value) {
        String statement = value == null ? "" : value;
        if (statement.trim().isEmpty()) {
            throw ApiException.validation("statement", "REQUIRED", "SQL 不能为空");
        }
        int bytes = statement.getBytes(StandardCharsets.UTF_8).length;
        if (bytes > properties.getExecution().getMaxStatementBytes()) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "STATEMENT_TOO_LARGE", "SQL 超过大小上限");
        }
        return statement;
    }

    private ApiException conflict(ScriptRecord current) {
        Map<String, Object> details = new LinkedHashMap<String, Object>();
        details.put("currentVersion", current.getVersion());
        details.put("currentUpdatedAt", current.getUpdatedAt());
        details.put("currentUpdatedByName", current.getUpdatedByName());
        return new ApiException(HttpStatus.CONFLICT, "VERSION_CONFLICT", "脚本已被更新", details);
    }

    private String clean(String value, int max, String field) {
        String text = value == null ? "" : value.trim();
        if (text.length() > max) throw ApiException.validation(field, "OUT_OF_RANGE", "筛选值过长");
        return text;
    }

    private String blank(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private String nulls(String value) {
        return value == null ? "" : value;
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
