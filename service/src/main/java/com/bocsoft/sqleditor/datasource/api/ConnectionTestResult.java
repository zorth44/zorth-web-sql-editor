package com.bocsoft.sqleditor.datasource.api;

public class ConnectionTestResult {
    private final String status;
    private final String serverVersion;
    private final long durationMs;
    private final String message;
    private final String failureCode;
    public ConnectionTestResult(String status, String serverVersion, long durationMs, String message, String failureCode) {
        this.status=status; this.serverVersion=serverVersion; this.durationMs=durationMs;
        this.message=message; this.failureCode=failureCode;
    }
    public static ConnectionTestResult success(String version, long duration) {
        return new ConnectionTestResult("SUCCESS", version, duration, "连接成功", null);
    }
    public static ConnectionTestResult failure(long duration, String message, String code) {
        return new ConnectionTestResult("FAILED", null, duration, message, code);
    }
    public String getStatus() { return status; }
    public String getServerVersion() { return serverVersion; }
    public long getDurationMs() { return durationMs; }
    public String getMessage() { return message; }
    public String getFailureCode() { return failureCode; }
}
