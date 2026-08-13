package com.bocsoft.sqleditor.common;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiError {
    private final String requestId;
    private final String code;
    private final String message;
    private final Object details;
    public ApiError(String requestId, String code, String message, Object details) {
        this.requestId = requestId;
        this.code = code;
        this.message = message;
        this.details = details;
    }
    public String getRequestId() { return requestId; }
    public String getCode() { return code; }
    public String getMessage() { return message; }
    public Object getDetails() { return details; }
}
