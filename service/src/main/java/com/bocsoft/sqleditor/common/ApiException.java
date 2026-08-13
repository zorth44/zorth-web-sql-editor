package com.bocsoft.sqleditor.common;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    private final Object details;

    public ApiException(HttpStatus status, String code, String message) {
        this(status, code, message, null);
    }

    public ApiException(HttpStatus status, String code, String message, Object details) {
        super(message);
        this.status = status;
        this.code = code;
        this.details = details;
    }

    public HttpStatus getStatus() { return status; }
    public String getCode() { return code; }
    public Object getDetails() { return details; }

    public static ApiException unauthenticated() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "登录已过期，请重新登录");
    }
    public static ApiException authUnavailable() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AUTH_SERVICE_UNAVAILABLE", "授权服务暂时不可用");
    }
    public static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "DATA_SOURCE_NOT_FOUND", "数据源不存在或已不可见");
    }
    public static ApiException validation(String field, String code, String message) {
        java.util.Map<String, Object> details = new java.util.LinkedHashMap<String, Object>();
        details.put("fieldErrors", java.util.Collections.singletonList(new FieldError(field, code, message)));
        return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "请求参数不合法", details);
    }
}
