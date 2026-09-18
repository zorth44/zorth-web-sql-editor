package com.bocsoft.sqleditor.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bocsoft.sqleditor.auth.AuthContext;
import com.bocsoft.sqleditor.common.ApiException;
import com.bocsoft.sqleditor.config.SqlEditorProperties;
import com.bocsoft.sqleditor.datasource.SavedDataSource;
import com.bocsoft.sqleditor.datasource.TargetConnectionProvider;
import com.bocsoft.sqleditor.datasource.connection.ConnectionConfiguration;
import com.bocsoft.sqleditor.engine.EngineSupport;
import com.bocsoft.sqleditor.engine.ExplainMode;
import com.bocsoft.sqleditor.engine.mysql.MysqlEngineSupport;
import com.bocsoft.sqleditor.execution.api.SqlExplainRequest;
import com.bocsoft.sqleditor.execution.api.SqlExecutionRequest;
import java.time.Instant;
import java.util.Collections;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class SqlExplainServiceTest {
    private TargetConnectionProvider targets;
    private SqlExecutionService executions;
    private SqlExplainService service;
    private SavedDataSource dataSource;
    private AuthContext auth;
    private SqlEditorProperties properties;

    @BeforeEach
    void setUp() {
        targets = mock(TargetConnectionProvider.class);
        executions = mock(SqlExecutionService.class);
        properties = new SqlEditorProperties();
        service = new SqlExplainService(targets, new SqlStatementClassifier(), executions, properties);
        dataSource = new SavedDataSource("ds-1", "orders", 1L, "orders",
            new ConnectionConfiguration("127.0.0.1", 3306, "u", "p", "orders", "DISABLED", 10, Collections.<String, String>emptyMap()));
        auth = new AuthContext("user-a", "a", "A", "product", "P", Instant.now().plusSeconds(60));
        when(targets.require(auth, "ds-1")).thenReturn(dataSource);
        when(targets.engine(dataSource)).thenReturn(new MysqlEngineSupport());
    }

    @Test
    void planPrepareRewritesSelectAndRejectsAnalyzeAndWrites() {
        SqlExecutionRequest inner = service.prepare(auth, explain("SELECT 1"), ExplainMode.PLAN);
        assertThat(inner.getStatement()).isEqualTo("EXPLAIN SELECT 1");
        assertThat(inner.getReadOnly()).isTrue();
        assertThat(inner.getSource()).isNull();

        assertThatThrownBy(() -> service.prepare(auth, explain("EXPLAIN ANALYZE SELECT 1"), ExplainMode.PLAN))
            .isInstanceOf(ApiException.class)
            .extracting("code").isEqualTo("EXPLAIN_ANALYZE_NOT_ALLOWED");
        assertThatThrownBy(() -> service.prepare(auth, explain("INSERT INTO t VALUES (1)"), ExplainMode.PLAN))
            .isInstanceOf(ApiException.class)
            .extracting("code").isEqualTo("EXPLAIN_STATEMENT_NOT_SUPPORTED");
        verify(executions, never()).executeForced(any(), any(), anyString(), anyString(), anyString());
    }

    @Test
    void analyzeIsDisabledByDefaultAndClampsTimeoutWhenEnabled() {
        assertThatThrownBy(() -> service.requireAnalyzeEnabled())
            .isInstanceOf(ApiException.class)
            .satisfies(error -> {
                ApiException api = (ApiException) error;
                assertThat(api.getCode()).isEqualTo("EXPLAIN_ANALYZE_DISABLED");
                assertThat(api.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
            });
        properties.getExplainAnalyze().setEnabled(true);
        properties.getExplainAnalyze().setTimeoutSeconds(15);
        SqlExplainRequest request = explain("SELECT 1");
        request.setTimeoutSeconds(60);
        SqlExecutionRequest inner = service.prepare(auth, request, ExplainMode.ANALYZE);
        assertThat(inner.getStatement()).isEqualTo("EXPLAIN ANALYZE SELECT 1");
        assertThat(inner.getTimeoutSeconds()).isEqualTo(15);
        assertThatThrownBy(() -> service.prepare(auth, explain("DELETE FROM t"), ExplainMode.ANALYZE))
            .extracting("code").isEqualTo("EXPLAIN_STATEMENT_NOT_SUPPORTED");
        assertThatThrownBy(() -> service.prepare(auth, explain("EXPLAIN ANALYZE SELECT 1"), ExplainMode.PLAN))
            .extracting("code").isEqualTo("EXPLAIN_ANALYZE_NOT_ALLOWED");
    }

    @Test
    void executeUsesForcedHistorySource() {
        SqlExecutionRequest inner = service.prepare(auth, explain("SELECT 1"), ExplainMode.PLAN);
        service.execute(auth, inner, "req", "10.0.0.1", ExplainMode.PLAN);
        verify(executions).executeForced(eq(auth), eq(inner), eq("req"), eq("10.0.0.1"), eq(ExecutionSource.AI_AGENT_EXPLAIN));
    }

    private SqlExplainRequest explain(String sql) {
        SqlExplainRequest request = new SqlExplainRequest();
        request.setExecutionId(UUID.randomUUID().toString());
        request.setDataSourceId("ds-1");
        request.setDatabase("orders");
        request.setStatement(sql);
        return request;
    }
}
