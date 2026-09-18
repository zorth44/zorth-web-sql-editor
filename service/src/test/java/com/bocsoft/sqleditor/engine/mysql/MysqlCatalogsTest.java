package com.bocsoft.sqleditor.engine.mysql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bocsoft.sqleditor.metadata.api.TableStats;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class MysqlCatalogsTest {
    @Test
    void mapsShowTableStatusToNeutralStats() throws Exception {
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        ResultSet rs = mock(ResultSet.class);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery("SHOW TABLE STATUS FROM `orders` WHERE Name = 'order_item'")).thenReturn(rs);
        when(rs.next()).thenReturn(true);
        when(rs.getString("Engine")).thenReturn("InnoDB");
        when(rs.getLong("Rows")).thenReturn(10L);
        when(rs.getLong("Data_length")).thenReturn(100L);
        when(rs.getLong("Index_length")).thenReturn(20L);
        when(rs.getLong("Auto_increment")).thenReturn(11L);
        Instant created = Instant.parse("2020-01-01T00:00:00Z");
        when(rs.getTimestamp("Create_time")).thenReturn(Timestamp.from(created));
        when(rs.getTimestamp("Update_time")).thenReturn(null);
        when(rs.getString("Comment")).thenReturn("订单明细");
        TableStats stats = new MysqlCatalogs().readStats(connection, "orders", "order_item");
        assertThat(stats.getEngine()).isEqualTo("InnoDB");
        assertThat(stats.getEstimatedRows()).isEqualTo(10L);
        assertThat(stats.getDataBytes()).isEqualTo(100L);
        assertThat(stats.getIndexBytes()).isEqualTo(20L);
        assertThat(stats.getAutoIncrement()).isEqualTo(11L);
        assertThat(stats.getCreateTime()).isEqualTo("2020-01-01T00:00:00Z");
        assertThat(stats.getUpdateTime()).isNull();
        assertThat(stats.getComment()).isEqualTo("订单明细");
    }

    @Test
    void statsFailureReturnsNull() throws Exception {
        Connection connection = mock(Connection.class);
        when(connection.createStatement()).thenThrow(new java.sql.SQLException("lock"));
        assertThat(new MysqlCatalogs().readStats(connection, "orders", "t")).isNull();
    }
}
