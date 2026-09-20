package com.bocsoft.sqleditor.engine;

public final class PlanFinding {
    private final String code;
    private final String message;

    public PlanFinding(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String getCode() { return code; }
    public String getMessage() { return message; }
}
