package com.bocsoft.sqleditor.agentapi.api;

public class AgentFinding {
    private final String code;
    private final String message;

    public AgentFinding(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String getCode() { return code; }
    public String getMessage() { return message; }
}
