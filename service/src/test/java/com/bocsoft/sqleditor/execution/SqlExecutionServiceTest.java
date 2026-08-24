package com.bocsoft.sqleditor.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.bocsoft.sqleditor.auth.AuthContext;
import com.bocsoft.sqleditor.common.ApiException;
import com.bocsoft.sqleditor.common.SqlEditorMetrics;
import com.bocsoft.sqleditor.config.SqlEditorProperties;
import com.bocsoft.sqleditor.datasource.SavedDataSource;
import com.bocsoft.sqleditor.datasource.TargetConnectionProvider;
import com.bocsoft.sqleditor.datasource.connection.ConnectionConfiguration;
import com.bocsoft.sqleditor.engine.EngineSupport;
import com.bocsoft.sqleditor.execution.api.SqlColumn;
import com.bocsoft.sqleditor.execution.api.SqlExecutionRequest;
import com.bocsoft.sqleditor.history.ExecutionHistoryService;
import com.bocsoft.sqleditor.history.persistence.ExecutionHistoryRecord;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import java.util.Collections;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SqlExecutionServiceTest {
    private TargetConnectionProvider targets;
    private EngineSupport engine;
    private ExecutionRegistry registry;
    private ExecutionHistoryService history;
    private SqlExecutionService service;
    private SavedDataSource dataSource;
    private AuthContext auth;

    @BeforeEach void setUp() {
        targets = mock(TargetConnectionProvider.class);
        engine = mock(EngineSupport.class);
        history = mock(ExecutionHistoryService.class);
        SqlEditorProperties properties = new SqlEditorProperties();
        properties.getExecution().setMaxConcurrentGlobal(10);
        properties.getExecution().setMaxConcurrentPerUser(5);
        properties.getExecution().setTimeoutSeconds(60);
        registry = new ExecutionRegistry(properties);
        service = new SqlExecutionService(targets, new SqlStatementClassifier(), mock(ResultSetReader.class),
            registry, history, properties, new SqlEditorMetrics(new SimpleMeterRegistry()));
        dataSource = new SavedDataSource("ds-1", "orders", 1L, "orders",
            new ConnectionConfiguration("127.0.0.1", 3306, "u", "p", "orders", "DISABLED", 10, Collections.<String, String>emptyMap()));
        auth = new AuthContext("user-a", "a", "A", "product", "P", Instant.now().plusSeconds(60));
        when(targets.require(auth, "ds-1")).thenReturn(dataSource);
        when(targets.engine(dataSource)).thenReturn(engine);
        when(engine.requireSingle(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        doNothing().when(engine).validateIdentifier(anyString(), anyString());
        when(history.exists(anyString())).thenReturn(false);
        when(history.start(anyString(), any(), any(), any(), anyString(), anyString(), any(), anyString(), anyString(), any()))
            .thenAnswer(invocation -> {
                ExecutionHistoryRecord record = new ExecutionHistoryRecord();
                record.setId(invocation.getArgument(0));
                return record;
            });
    }

    @ParameterizedTest
    @ValueSource(strings = {"INSERT INTO t VALUES (1)", "UPDATE t SET a=1", "DELETE FROM t", "DROP TABLE t", "CALL proc()"})
    void readOnlyRejectsWritesBeforeAcquire(String sql) throws Exception {
        SqlExecutionRequest request = request(sql);
        request.setReadOnly(true);
        assertThatThrownBy(() -> service.execute(auth, request, "req", "10.0.0.1"))
            .isInstanceOf(ApiException.class)
            .extracting("code").isEqualTo("READ_ONLY_VIOLATION");
        verify(history, never()).start(anyString(), any(), any(), any(), anyString(), anyString(), any(), anyString(), anyString(), any());
        verify(targets, never()).borrow(any());
        assertThat(registry.count("ds-1")).isZero();
    }

    @Test void omittedReadOnlyAllowsInsert() throws Exception {
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        when(targets.borrow(dataSource)).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.execute(anyString())).thenReturn(false);
        when(statement.getUpdateCount()).thenReturn(1);
        SqlExecutionRequest request = request("INSERT INTO t VALUES (1)");
        service.execute(auth, request, "req", "10.0.0.1");
        verify(connection, never()).setReadOnly(true);
        verify(connection).setAutoCommit(true);
        verify(history).start(eq(request.getExecutionId()), eq(auth), eq(dataSource), eq("orders"),
            eq("EXECUTE"), eq("INSERT INTO t VALUES (1)"), eq(StatementType.INSERT), eq("req"),
            eq(ExecutionSource.WEB_SQL_EDITOR), eq("10.0.0.1"));
    }

    @Test void readOnlySelectSetsConnectionReadOnlyAndPersistsAgentSource() throws Exception {
        executeReadOnlyQuery("SELECT 1", ExecutionSource.AI_AGENT, "10.1.2.3");
    }

    @Test void readOnlyAllowsWithAndShow() throws Exception {
        executeReadOnlyQuery("WITH x AS (SELECT 1 AS n) SELECT n FROM x", ExecutionSource.WEB_SQL_EDITOR, "127.0.0.1");
        executeReadOnlyQuery("SHOW TABLES", ExecutionSource.WEB_SQL_EDITOR, "127.0.0.1");
    }

    private void executeReadOnlyQuery(String sql, String source, String clientIp) throws Exception {
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        ResultSetReader reader = mock(ResultSetReader.class);
        SqlEditorProperties properties = new SqlEditorProperties();
        properties.getExecution().setMaxConcurrentGlobal(10);
        properties.getExecution().setMaxConcurrentPerUser(5);
        registry = new ExecutionRegistry(properties);
        service = new SqlExecutionService(targets, new SqlStatementClassifier(), reader, registry, history,
            properties, new SqlEditorMetrics(new SimpleMeterRegistry()));
        when(targets.borrow(dataSource)).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.execute(anyString())).thenReturn(true);
        when(statement.getResultSet()).thenReturn(mock(java.sql.ResultSet.class));
        when(reader.read(any(), anyInt(), anyLong()))
            .thenReturn(new ResultSetReader.ReadResult(Collections.singletonList(new SqlColumn("n", "n", "INTEGER", "INT")),
                Collections.singletonList(Collections.<Object>singletonList(1)), false, 8));
        SqlExecutionRequest request = request(sql);
        request.setReadOnly(true);
        request.setSource(source);
        service.execute(auth, request, "req", clientIp);
        verify(connection).setReadOnly(true);
        verify(history).start(eq(request.getExecutionId()), eq(auth), eq(dataSource), eq("orders"),
            eq("EXECUTE"), eq(sql), eq(StatementType.SELECT), eq("req"), eq(source), eq(clientIp));
    }

    @Test void readOnlyUpdateCountAfterExecuteFails() throws Exception {
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        when(targets.borrow(dataSource)).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.execute(anyString())).thenReturn(false);
        SqlExecutionRequest request = request("SELECT 1");
        request.setReadOnly(true);
        assertThatThrownBy(() -> service.execute(auth, request, "req", "10.0.0.1"))
            .isInstanceOf(ApiException.class)
            .extracting("code").isEqualTo("READ_ONLY_VIOLATION");
        verify(history).failure(any(), eq("FAILED"), anyLong(), any());
    }

    @Test void timeoutSecondsMustBeWithinConfiguredRange() {
        SqlExecutionRequest request = request("SELECT 1");
        request.setTimeoutSeconds(0);
        assertThatThrownBy(() -> service.effectiveTimeoutSeconds(request))
            .isInstanceOf(ApiException.class)
            .extracting("code").isEqualTo("VALIDATION_FAILED");
        request.setTimeoutSeconds(61);
        assertThatThrownBy(() -> service.effectiveTimeoutSeconds(request))
            .isInstanceOf(ApiException.class)
            .extracting("code").isEqualTo("VALIDATION_FAILED");
        request.setTimeoutSeconds(10);
        assertThat(service.effectiveTimeoutSeconds(request)).isEqualTo(10);
        assertThat(service.asyncTimeoutMs(request)).isEqualTo(15000L);
        request.setTimeoutSeconds(null);
        assertThat(service.effectiveTimeoutSeconds(request)).isEqualTo(60);
    }

    @Test void requestedTimeoutIsAppliedToJdbc() throws Exception {
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        ResultSetReader reader = mock(ResultSetReader.class);
        SqlEditorProperties properties = new SqlEditorProperties();
        properties.getExecution().setMaxConcurrentGlobal(10);
        properties.getExecution().setMaxConcurrentPerUser(5);
        registry = new ExecutionRegistry(properties);
        service = new SqlExecutionService(targets, new SqlStatementClassifier(), reader, registry, history,
            properties, new SqlEditorMetrics(new SimpleMeterRegistry()));
        when(targets.borrow(dataSource)).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.execute(anyString())).thenReturn(true);
        when(statement.getResultSet()).thenReturn(mock(java.sql.ResultSet.class));
        when(reader.read(any(), anyInt(), anyLong()))
            .thenReturn(new ResultSetReader.ReadResult(Collections.<SqlColumn>emptyList(),
                Collections.<java.util.List<Object>>emptyList(), false, 0));
        SqlExecutionRequest request = request("SELECT 1");
        request.setTimeoutSeconds(10);
        service.execute(auth, request, "req", "127.0.0.1");
        verify(statement).setQueryTimeout(10);
    }

    @Test void unknownSourceIsRejectedBeforeAcquire() {
        SqlExecutionRequest request = request("SELECT 1");
        request.setSource("BROWSER");
        assertThatThrownBy(() -> service.execute(auth, request, "req", "10.0.0.1"))
            .isInstanceOf(ApiException.class)
            .extracting("code").isEqualTo("VALIDATION_FAILED");
        verify(history, never()).start(anyString(), any(), any(), any(), anyString(), anyString(), any(), anyString(), anyString(), any());
        assertThat(registry.count("ds-1")).isZero();
    }

    private SqlExecutionRequest request(String sql) {
        SqlExecutionRequest request = new SqlExecutionRequest();
        request.setExecutionId(UUID.randomUUID().toString());
        request.setDataSourceId("ds-1");
        request.setDatabase("orders");
        request.setStatement(sql);
        request.setRowLimit(100);
        return request;
    }
}
