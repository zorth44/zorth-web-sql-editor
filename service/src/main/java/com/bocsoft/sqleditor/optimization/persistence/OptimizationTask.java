package com.bocsoft.sqleditor.optimization.persistence;

import java.time.Instant;
import com.bocsoft.sqleditor.optimization.api.TaskStatus;

/** Internal persisted snapshot; never serialized directly to an API. */
public class OptimizationTask {
    public String id, userId, productId, name, datasourceId, datasourceName, engineId, database, sql;
    public String sourceTaskId, idempotencyKey, fingerprint, attemptId, reportJson, errorCode, errorMessage, requestId;
    public long version;
    public Integer schemaVersion, policyCount, performanceCount, limitationCount;
    public TaskStatus status;
    public Instant createdAt, startedAt, completedAt, expiresAt;
}
