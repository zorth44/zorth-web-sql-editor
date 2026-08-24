package com.bocsoft.sqleditor.execution;

import com.bocsoft.sqleditor.common.ApiException;

public final class ExecutionSource {
    public static final String WEB_SQL_EDITOR = "WEB_SQL_EDITOR";
    public static final String AI_AGENT = "AI_AGENT";

    private ExecutionSource() {}

    public static String normalize(String raw) {
        if (raw == null || raw.trim().isEmpty()) return WEB_SQL_EDITOR;
        String value = raw.trim();
        if (WEB_SQL_EDITOR.equals(value) || AI_AGENT.equals(value)) return value;
        throw ApiException.validation("source", "INVALID", "source 仅支持 WEB_SQL_EDITOR 或 AI_AGENT");
    }
}
