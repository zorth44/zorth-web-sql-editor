package com.bocsoft.sqleditor.common;

import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> api(ApiException exception, HttpServletRequest request) {
        return response(exception.getStatus(), exception.getCode(), exception.getMessage(),
            exception.getDetails(), request);
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ResponseEntity<ApiError> invalidBinding(Exception exception, HttpServletRequest request) {
        List<FieldError> errors = new ArrayList<FieldError>();
        org.springframework.validation.BindingResult result = exception instanceof MethodArgumentNotValidException
            ? ((MethodArgumentNotValidException) exception).getBindingResult()
            : ((BindException) exception).getBindingResult();
        for (org.springframework.validation.FieldError item : result.getFieldErrors()) {
            errors.add(new FieldError(item.getField(), stableValidationCode(item.getCode()),
                item.getDefaultMessage() == null ? "字段值不合法" : item.getDefaultMessage()));
        }
        return validation(errors, request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> invalidConstraint(ConstraintViolationException exception,
                                                       HttpServletRequest request) {
        List<FieldError> errors = new ArrayList<FieldError>();
        exception.getConstraintViolations().forEach(item -> errors.add(new FieldError(
            lastSegment(item.getPropertyPath().toString()), "INVALID", item.getMessage())));
        return validation(errors, request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> missingParameter(MissingServletRequestParameterException exception,
                                                      HttpServletRequest request) {
        return validation(java.util.Collections.singletonList(new FieldError(
            exception.getParameterName(), "REQUIRED", "缺少必填参数")), request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> unreadable(HttpMessageNotReadableException exception,
                                                HttpServletRequest request) {
        Throwable cause = exception.getMostSpecificCause();
        if (cause instanceof UnrecognizedPropertyException) {
            String field = ((UnrecognizedPropertyException) cause).getPropertyName();
            return validation(java.util.Collections.singletonList(
                new FieldError(field, "UNKNOWN_FIELD", "不允许提交该字段")), request);
        }
        return validation(java.util.Collections.singletonList(
            new FieldError("body", "MALFORMED_JSON", "请求 JSON 格式不合法")), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unexpected(Exception exception, HttpServletRequest request) {
        LOG.error("Unhandled request failure requestId={} type={}", requestId(request),
            exception.getClass().getName());
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "服务暂时不可用",
            null, request);
    }

    private ResponseEntity<ApiError> validation(List<FieldError> errors, HttpServletRequest request) {
        Map<String, Object> details = new LinkedHashMap<String, Object>();
        details.put("fieldErrors", errors);
        return response(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "请求参数不合法", details, request);
    }

    private ResponseEntity<ApiError> response(HttpStatus status, String code, String message,
                                               Object details, HttpServletRequest request) {
        return ResponseEntity.status(status).body(new ApiError(requestId(request), code, message, details));
    }

    private String requestId(HttpServletRequest request) {
        Object value = request.getAttribute(RequestIds.ATTRIBUTE);
        return value == null ? "unknown" : value.toString();
    }

    private String stableValidationCode(String code) {
        if ("NotNull".equals(code) || "NotBlank".equals(code) || "NotEmpty".equals(code)) return "REQUIRED";
        if ("Min".equals(code) || "Max".equals(code) || "Size".equals(code)) return "OUT_OF_RANGE";
        return "INVALID";
    }

    private String lastSegment(String value) {
        int dot = value.lastIndexOf('.');
        return dot < 0 ? value : value.substring(dot + 1);
    }
}
