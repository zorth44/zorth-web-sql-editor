package com.bocsoft.sqleditor.engine;

public final class ConnectionFailure {
    private final String code;
    private final String message;
    public ConnectionFailure(String code, String message) {
        this.code = code;
        this.message = message;
    }
    public String getCode() { return code; }
    public String getMessage() { return message; }
}
