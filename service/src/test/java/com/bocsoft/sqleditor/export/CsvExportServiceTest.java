package com.bocsoft.sqleditor.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.bocsoft.sqleditor.auth.AuthContext;
import com.bocsoft.sqleditor.config.SqlEditorProperties;
import com.bocsoft.sqleditor.datasource.SavedDataSource;
import com.bocsoft.sqleditor.datasource.TargetConnectionProvider;
import com.bocsoft.sqleditor.datasource.connection.ConnectionConfiguration;
import com.bocsoft.sqleditor.engine.EngineSupport;
import com.bocsoft.sqleditor.execution.JdbcValueEncoder;
import com.bocsoft.sqleditor.execution.StatementType;
import com.bocsoft.sqleditor.export.api.SqlExportRequest;
import com.bocsoft.sqleditor.history.ExecutionHistoryService;
import com.bocsoft.sqleditor.history.persistence.ExecutionHistoryRecord;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Instant;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class CsvExportServiceTest {
    private TargetConnectionProvider targets;
    private EngineSupport engine;
    private ExecutionHistoryService history;
    private CsvExportService service;
    private SavedDataSource dataSource;
    private AuthContext auth;
    private ExecutionHistoryRecord original;
    private Connection connection;
    private Statement statement;

    @BeforeEach void setUp() throws Exception {
        targets = mock(TargetConnectionProvider.class);
        engine = mock(EngineSupport.class);
        history = mock(ExecutionHistoryService.class);
        service = new CsvExportService(history, targets, new CsvEncoder(new SqlEditorProperties()),
            new SqlEditorProperties(), new JdbcValueEncoder());
        dataSource = new SavedDataSource("ds-1", "orders", 1L, "orders",
            new ConnectionConfiguration("127.0.0.1", 3306, "u", "p", "orders", "DISABLED", 10,
                Collections.<String, String>emptyMap()));
        auth = new AuthContext("user-a", "a", "A", "product", "P", Instant.now().plusSeconds(60));
        original = new ExecutionHistoryRecord();
        original.setId("exec-1");
        original.setStatus("SUCCESS");
        original.setResultKind("RESULT_SET");
        original.setDataSourceId("ds-1");
        original.setDatabaseName("orders");
        original.setStatementText("select n from t");
        original.setStatementType(StatementType.SELECT.name());
        when(history.owned("exec-1", "user-a")).thenReturn(original);
        when(targets.require(auth, "ds-1")).thenReturn(dataSource);
        when(targets.engine(dataSource)).thenReturn(engine);
        when(history.start(anyString(), eq(auth), eq(dataSource), eq("orders"), eq("EXPORT"),
            eq("select n from t"), eq(StatementType.SELECT), eq("req"), anyString(), eq("10.0.0.1")))
            .thenAnswer(invocation -> {
                ExecutionHistoryRecord record = new ExecutionHistoryRecord();
                record.setId(invocation.getArgument(0));
                return record;
            });
        connection = mock(Connection.class);
        statement = mock(Statement.class);
        when(targets.borrow(dataSource)).thenReturn(connection);
        when(connection.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)).thenReturn(statement);
        when(engine.streamingFetchSize()).thenReturn(100);
        stubResultSet(1);
    }

    @Test void postgresExportTurnsAutocommitOffThenCommits() throws Exception {
        when(engine.streamingRequiresAutoCommitOff()).thenReturn(true);
        writeExport();
        InOrder order = inOrder(engine, connection, statement);
        order.verify(engine).applyNamespace(connection, "orders");
        order.verify(connection).setAutoCommit(false);
        order.verify(connection).createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
        order.verify(statement).setFetchSize(100);
        order.verify(connection).commit();
        order.verify(connection).setAutoCommit(true);
        verify(targets).release(dataSource, connection);
        verify(history).success(any(ExecutionHistoryRecord.class), eq("RESULT_SET"), eq(1L), isNull(), eq(false), anyLong());
    }

    @Test void mysqlExportLeavesAutocommitAlone() throws Exception {
        when(engine.streamingRequiresAutoCommitOff()).thenReturn(false);
        writeExport();
        verify(connection, never()).setAutoCommit(anyBoolean());
        verify(connection, never()).commit();
        verify(statement).setFetchSize(100);
        verify(targets).release(dataSource, connection);
        verify(history).success(any(ExecutionHistoryRecord.class), eq("RESULT_SET"), eq(1L), isNull(), eq(false), anyLong());
    }

    @Test void failedExportDoesNotCommitStreamingTransaction() throws Exception {
        when(engine.streamingRequiresAutoCommitOff()).thenReturn(true);
        when(statement.execute("select n from t")).thenThrow(new SQLException("boom"));
        assertThatThrownBy(this::writeExport).isInstanceOf(IOException.class).hasMessage("CSV 导出失败");
        verify(connection).setAutoCommit(false);
        verify(connection, never()).commit();
        verify(history).failure(any(ExecutionHistoryRecord.class), eq("FAILED"), anyLong(), any(SQLException.class));
        verify(targets).release(dataSource, connection);
    }

    @Test void cancelledExportCancelsStatementAndDoesNotCommit() throws Exception {
        when(engine.streamingRequiresAutoCommitOff()).thenReturn(true);
        OutputStream failing = new OutputStream() {
            @Override public void write(int b) throws IOException { throw new IOException("disconnected"); }
            @Override public void write(byte[] b, int off, int len) throws IOException { throw new IOException("disconnected"); }
        };
        CsvExportService.PreparedExport prepared = service.prepare(auth, request(), "req", "10.0.0.1");
        assertThatThrownBy(() -> prepared.getBody().writeTo(failing))
            .isInstanceOf(IOException.class).hasMessage("disconnected");
        verify(connection).setAutoCommit(false);
        verify(connection, never()).commit();
        verify(statement).cancel();
        verify(history).failure(any(ExecutionHistoryRecord.class), eq("CANCELLED"), anyLong(), any(SQLException.class));
        verify(targets).release(dataSource, connection);
    }

    private void writeExport() throws Exception {
        CsvExportService.PreparedExport prepared = service.prepare(auth, request(), "req", "10.0.0.1");
        assertThat(prepared.getFilename()).isEqualTo("orders-orders.csv");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        prepared.getBody().writeTo(out);
        assertThat(out.size()).isGreaterThan(3);
    }

    private void stubResultSet(int value) throws Exception {
        ResultSet rs = mock(ResultSet.class);
        ResultSetMetaData meta = mock(ResultSetMetaData.class);
        when(statement.execute("select n from t")).thenReturn(true);
        when(statement.getResultSet()).thenReturn(rs);
        when(rs.getMetaData()).thenReturn(meta);
        when(meta.getColumnCount()).thenReturn(1);
        when(meta.getColumnLabel(1)).thenReturn("n");
        when(meta.getColumnType(1)).thenReturn(Types.INTEGER);
        when(rs.next()).thenReturn(true, false);
        when(rs.getObject(1)).thenReturn(Integer.valueOf(value));
    }

    private SqlExportRequest request() {
        SqlExportRequest request = new SqlExportRequest();
        request.setExecutionId("exec-1");
        request.setRowLimit(1000);
        return request;
    }
}
