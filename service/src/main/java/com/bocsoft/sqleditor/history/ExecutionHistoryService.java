package com.bocsoft.sqleditor.history;

import com.bocsoft.sqleditor.auth.AuthContext;
import com.bocsoft.sqleditor.common.ApiException;
import com.bocsoft.sqleditor.datasource.SavedDataSource;
import com.bocsoft.sqleditor.execution.StatementType;
import com.bocsoft.sqleditor.history.persistence.ExecutionHistoryMapper;
import com.bocsoft.sqleditor.history.persistence.ExecutionHistoryRecord;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.SQLException;
import java.time.Clock;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ExecutionHistoryService {
    private final ExecutionHistoryMapper mapper;
    private final Clock clock;

    public ExecutionHistoryService(ExecutionHistoryMapper mapper, Clock clock) {
        this.mapper = mapper;
        this.clock = clock;
    }

    public boolean exists(String id) { return mapper.exists(id) > 0; }

    public ExecutionHistoryRecord start(String id, AuthContext auth, SavedDataSource source, String database,
                                        String operation, String statement, StatementType type, String requestId,
                                        String executionSource, String clientIp) {
        ExecutionHistoryRecord r = new ExecutionHistoryRecord();
        r.setId(id);
        r.setUserId(auth.getUserId());
        r.setUsername(auth.getUsername());
        r.setProductId(auth.getProductId());
        r.setDataSourceId(source.getId());
        r.setDataSourceName(source.getName());
        r.setDatabaseName(blank(database));
        r.setOperation(operation);
        r.setSource(executionSource);
        r.setStatementText(statement);
        r.setStatementHash(hash(statement));
        r.setStatementType(type.name());
        r.setStatus("RUNNING");
        r.setRequestId(requestId == null ? UUID.randomUUID().toString() : requestId);
        r.setClientIp(clientIp);
        r.setStartedAt(clock.instant());
        try { mapper.insert(r); }
        catch (DuplicateKeyException e) { throw new ApiException(HttpStatus.CONFLICT, "EXECUTION_ID_CONFLICT", "执行 ID 已被使用"); }
        return r;
    }

    public void success(ExecutionHistoryRecord r, String kind, long returned, Long affected, boolean truncated, long duration) {
        r.setStatus("SUCCESS");
        r.setResultKind(kind);
        r.setReturnedRows("RESULT_SET".equals(kind) ? returned : null);
        r.setAffectedRows(affected);
        r.setTruncated(truncated);
        finish(r, duration);
    }

    public void failure(ExecutionHistoryRecord r, String status, long duration, SQLException error) {
        r.setStatus(status);
        if (error != null) {
            r.setVendorErrorCode(error.getErrorCode());
            r.setSqlState(truncate(error.getSQLState(), 10));
            r.setErrorMessage(truncate(error.getMessage(), 1000));
        }
        finish(r, duration);
    }

    private void finish(ExecutionHistoryRecord r, long duration) {
        r.setDurationMs(duration);
        r.setFinishedAt(clock.instant());
        mapper.finish(r);
    }

    public ExecutionHistoryRecord owned(String id, String user) { return mapper.findOwned(id, user); }
    private String blank(String v) { return v == null || v.trim().isEmpty() ? null : v.trim(); }
    private String truncate(String v, int n) { return v == null ? null : (v.length() <= n ? v : v.substring(0, n)); }
    private String hash(String s) {
        try {
            byte[] b = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder x = new StringBuilder();
            for (byte v : b) x.append(String.format("%02x", v & 255));
            return x.toString();
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
}
