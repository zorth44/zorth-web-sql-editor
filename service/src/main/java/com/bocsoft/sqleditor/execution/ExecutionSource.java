package com.bocsoft.sqleditor.execution;

import com.bocsoft.sqleditor.common.ApiException;

public final class ExecutionSource {
    public static final String WEB_SQL_EDITOR = "WEB_SQL_EDITOR";
    public static final String AI_AGENT = "AI_AGENT";
    public static final String AI_AGENT_EXPLAIN = "AI_AGENT_EXPLAIN";
    public static final String AI_AGENT_EXPLAIN_ANALYZE = "AI_AGENT_EXPLAIN_ANALYZE";

    private ExecutionSource() {}

    public static String normalizeClient(String raw) {
        if (raw == null || raw.trim().isEmpty()) return WEB_SQL_EDITOR;
        String value = raw.trim();
        if (WEB_SQL_EDITOR.equals(value) || AI_AGENT.equals(value)) return value;
        throw ApiException.validation("source", "INVALID", "source 仅支持 WEB_SQL_EDITOR 或 AI_AGENT");
    }

    public static String requirePersisted(String raw) {
        if (WEB_SQL_EDITOR.equals(raw) || AI_AGENT.equals(raw)
            || AI_AGENT_EXPLAIN.equals(raw) || AI_AGENT_EXPLAIN_ANALYZE.equals(raw)) {
            return raw;
        }
        throw new IllegalArgumentException("unsupported execution source");
    }
}
