package com.bocsoft.sqleditor.engine.postgres;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bocsoft.sqleditor.metadata.api.TableStats;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import org.junit.jupiter.api.Test;

class PostgresCatalogsTest {
    @Test
    void mapsPgClassEstimateAndSizes() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(true);
        when(rs.getString("engine")).thenReturn("heap");
        when(rs.getLong("estimated_rows")).thenReturn(5L);
        when(rs.getLong("data_bytes")).thenReturn(8192L);
        when(rs.getLong("index_bytes")).thenReturn(16L);
        when(rs.getString("comment")).thenReturn(null);
        TableStats stats = new PostgresCatalogs().readStats(connection, "sales", "order_item");
        assertThat(stats.getEngine()).isEqualTo("heap");
        assertThat(stats.getEstimatedRows()).isEqualTo(5L);
        assertThat(stats.getDataBytes()).isEqualTo(8192L);
        assertThat(stats.getIndexBytes()).isEqualTo(16L);
        assertThat(stats.getAutoIncrement()).isNull();
    }

    @Test
    void missingRelationReturnsNullStats() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(false);
        assertThat(new PostgresCatalogs().readStats(connection, "sales", "missing")).isNull();
    }

    @Test
    void viewStatsAllowNullSizes() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(true);
        when(rs.getString("engine")).thenReturn("view");
        when(rs.getLong("estimated_rows")).thenReturn(0L);
        when(rs.getLong("data_bytes")).thenReturn(0L);
        when(rs.getLong("index_bytes")).thenReturn(0L);
        when(rs.wasNull()).thenReturn(false, true, true);
        when(rs.getString("comment")).thenReturn(null);
        TableStats stats = new PostgresCatalogs().readStats(connection, "sales", "order_view");
        assertThat(stats.getEngine()).isEqualTo("view");
        assertThat(stats.getEstimatedRows()).isEqualTo(0L);
        assertThat(stats.getDataBytes()).isNull();
        assertThat(stats.getIndexBytes()).isNull();
    }
}
