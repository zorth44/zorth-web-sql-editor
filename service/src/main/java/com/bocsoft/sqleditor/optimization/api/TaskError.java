package com.bocsoft.sqleditor.optimization.api;

@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS)
public class TaskError {
    private String code;
    private String message;
    private String requestId;
    public String getCode() { return code; }
    public void setCode(String value) { code = value; }
    public String getMessage() { return message; }
    public void setMessage(String value) { message = value; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String value) { requestId = value; }
}
