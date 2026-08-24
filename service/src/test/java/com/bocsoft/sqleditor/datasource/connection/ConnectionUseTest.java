package com.bocsoft.sqleditor.datasource.connection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.bocsoft.sqleditor.engine.mysql.MysqlEngineSupport;
import java.sql.Connection;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;

class ConnectionUseTest {
    private final MysqlEngineSupport engine = new MysqlEngineSupport();

    @Test
    void rollsBackAndRestoresSessionBeforeClose() throws Exception {
        Connection connection = mock(Connection.class);
        when(connection.getAutoCommit()).thenReturn(false);
        String result = ConnectionUse.execute(connection, "orders", engine, value -> {
            value.setReadOnly(true);
            return "ok";
        });
        assertThat(result).isEqualTo("ok");
        verify(connection).rollback();
        verify(connection).setAutoCommit(true);
        verify(connection).setReadOnly(false);
        verify(connection).setCatalog("orders");
        verify(connection).clearWarnings();
        verify(connection).close();
        verify(connection, never()).getCatalog();
    }

    @Test
    void skipsSetCatalogWhenDefaultCatalogIsNull() throws Exception {
        Connection connection = mock(Connection.class);
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.getCatalog()).thenReturn(null);
        String result = ConnectionUse.execute(connection, null, engine, value -> "ok");
        assertThat(result).isEqualTo("ok");
        verify(connection, never()).setCatalog(any());
        verify(connection).close();
    }

    @Test
    void skipsSetCatalogWhenDefaultCatalogIsBlank() throws Exception {
        Connection connection = mock(Connection.class);
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.getCatalog()).thenReturn("  ");
        String result = ConnectionUse.execute(connection, "  ", engine, value -> "ok");
        assertThat(result).isEqualTo("ok");
        verify(connection, never()).setCatalog(any());
        verify(connection).close();
    }

    @Test
    void evictsConnectionWhenCatalogWasSwitchedAndDefaultIsBlank() throws Exception {
        Connection connection = mock(Connection.class);
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.getCatalog()).thenReturn("orders");
        ConnectionUse.Evict evict = mock(ConnectionUse.Evict.class);
        String result = ConnectionUse.execute(connection, null, engine, evict, value -> "ok");
        assertThat(result).isEqualTo("ok");
        verify(connection, never()).setCatalog(any());
        verify(evict).evict(connection);
        verify(connection, never()).close();
    }

    @Test
    void closesWhenCatalogWasSwitchedAndNoEvictCallback() throws Exception {
        Connection connection = mock(Connection.class);
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.getCatalog()).thenReturn("orders");
        String result = ConnectionUse.execute(connection, null, engine, value -> "ok");
        assertThat(result).isEqualTo("ok");
        verify(connection).close();
    }

    @Test
    void keepsSuccessfulResultWhenCloseFails() throws Exception {
        Connection connection = mock(Connection.class);
        when(connection.getAutoCommit()).thenReturn(true);
        doThrow(new SQLException("close failed")).when(connection).close();
        String result = ConnectionUse.execute(connection, "orders", engine, value -> "ok");
        assertThat(result).isEqualTo("ok");
        verify(connection).setCatalog("orders");
        verify(connection).close();
    }
}
