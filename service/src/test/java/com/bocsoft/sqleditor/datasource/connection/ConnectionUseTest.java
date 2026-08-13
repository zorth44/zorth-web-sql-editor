package com.bocsoft.sqleditor.datasource.connection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.sql.Connection;
import org.junit.jupiter.api.Test;

class ConnectionUseTest {
    @Test void rollsBackAndRestoresSessionBeforeClose()throws Exception{Connection connection=mock(Connection.class);when(connection.getAutoCommit()).thenReturn(false);String result=ConnectionUse.execute(connection,"orders",value->{value.setReadOnly(true);return "ok";});assertThat(result).isEqualTo("ok");verify(connection).rollback();verify(connection).setAutoCommit(true);verify(connection).setCatalog("orders");verify(connection).clearWarnings();verify(connection).close();}
}
