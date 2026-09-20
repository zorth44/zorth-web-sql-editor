package com.bocsoft.sqleditor.execution;

public final class SqlFinding {
    private final String code;
    private final String message;

    public SqlFinding(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String getCode() { return code; }
    public String getMessage() { return message; }
}
