package com.bocsoft.sqleditor.datasource.connection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.sql.Connection;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;

class ConnectionUseTest {
    @Test
    void rollsBackAndRestoresSessionBeforeClose() throws Exception {
        Connection connection = mock(Connection.class);
        when(connection.getAutoCommit()).thenReturn(false);
        String result = ConnectionUse.execute(connection, "orders", value -> {
            value.setReadOnly(true);
            return "ok";
        });
        assertThat(result).isEqualTo("ok");
        verify(connection).rollback();
        verify(connection).setAutoCommit(true);
        verify(connection).setCatalog("orders");
        verify(connection).clearWarnings();
        verify(connection).close();
        verify(connection, never()).abort(any());
        verify(connection, never()).getCatalog();
    }

    @Test
    void skipsSetCatalogWhenDefaultCatalogIsNull() throws Exception {
        Connection connection = mock(Connection.class);
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.getCatalog()).thenReturn(null);
        String result = ConnectionUse.execute(connection, null, value -> "ok");
        assertThat(result).isEqualTo("ok");
        verify(connection, never()).setCatalog(any());
        verify(connection, never()).abort(any());
        verify(connection).close();
    }

    @Test
    void skipsSetCatalogWhenDefaultCatalogIsBlank() throws Exception {
        Connection connection = mock(Connection.class);
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.getCatalog()).thenReturn("  ");
        String result = ConnectionUse.execute(connection, "  ", value -> "ok");
        assertThat(result).isEqualTo("ok");
        verify(connection, never()).setCatalog(any());
        verify(connection, never()).abort(any());
        verify(connection).close();
    }

    @Test
    void discardsConnectionWhenCatalogWasSwitchedAndDefaultIsBlank() throws Exception {
        Connection connection = mock(Connection.class);
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.getCatalog()).thenReturn("orders");
        String result = ConnectionUse.execute(connection, null, value -> "ok");
        assertThat(result).isEqualTo("ok");
        verify(connection, never()).setCatalog(any());
        verify(connection).abort(any());
        verify(connection).close();
    }

    @Test
    void keepsSuccessfulResultWhenCloseFails() throws Exception {
        Connection connection = mock(Connection.class);
        when(connection.getAutoCommit()).thenReturn(true);
        doThrow(new SQLException("close failed")).when(connection).close();
        String result = ConnectionUse.execute(connection, "orders", value -> "ok");
        assertThat(result).isEqualTo("ok");
        verify(connection).setCatalog("orders");
        verify(connection).close();
    }
}
