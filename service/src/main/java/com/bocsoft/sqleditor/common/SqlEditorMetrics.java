package com.bocsoft.sqleditor.common;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class SqlEditorMetrics {
    private final MeterRegistry registry;
    public SqlEditorMetrics(MeterRegistry registry){this.registry=registry;}
    public void auth(String outcome){registry.counter("sql_editor_auth_total","outcome",safe(outcome)).increment();}
    public void dataSource(String operation,String outcome){registry.counter("sql_editor_data_source_total","operation",safe(operation),"outcome",safe(outcome)).increment();}
    public void connectionTest(String outcome,String failureCode,long durationMs){String safeOutcome=safe(outcome);registry.counter("sql_editor_connection_test_total","outcome",safeOutcome,"failure",safeFailure(failureCode)).increment();Timer.builder("sql_editor_connection_test_duration").tag("outcome",safeOutcome).register(registry).record(Duration.ofMillis(Math.max(0,durationMs)));}
    public void execution(String outcome,String type,long durationMs){registry.counter("sql_editor_execution_total","outcome",safe(outcome),"type",safeType(type)).increment();Timer.builder("sql_editor_execution_duration").tag("outcome",safe(outcome)).register(registry).record(Duration.ofMillis(Math.max(0,durationMs)));}
    private String safe(String value){if(value==null)return "unknown";switch(value){case"success":case"invalid":case"unavailable":case"list":case"detail":case"create":case"update":case"delete":case"failed":case"timeout":case"cancelled":return value;default:return"other";}}
    private String safeType(String value){if(value==null)return"OTHER";switch(value){case"SELECT":case"INSERT":case"UPDATE":case"DELETE":case"REPLACE":case"DDL":case"OTHER":return value;default:return"OTHER";}}
    private String safeFailure(String value){if(value==null)return "none";switch(value){case"AUTHENTICATION_FAILED":case"CONNECTION_REFUSED":case"CONNECTION_TIMEOUT":case"DATABASE_NOT_FOUND":case"TLS_FAILED":case"CONNECTION_FAILED":return value;default:return"other";}}
}
