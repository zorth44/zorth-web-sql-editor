package com.bocsoft.sqleditor.optimization.persistence;

import java.time.Instant;

public class OptimizationAttempt {
    public OptimizationTask task;
    public String id, sessionId, requestId;
    public Instant deadline;
}
