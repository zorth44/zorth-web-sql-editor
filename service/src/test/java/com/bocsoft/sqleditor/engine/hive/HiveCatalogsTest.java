package com.bocsoft.sqleditor.engine.hive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bocsoft.sqleditor.common.ApiException;
import com.bocsoft.sqleditor.metadata.api.ColumnItem;
import com.bocsoft.sqleditor.metadata.api.DatabaseItem;
import com.bocsoft.sqleditor.metadata.api.TableDetailResponse;
import com.bocsoft.sqleditor.metadata.api.TableItem;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import org.junit.jupiter.api.Test;

class HiveCatalogsTest {
    private final HiveCatalogs catalogs = new HiveCatalogs();

    @Test void listsDatabasesFromShowDatabases() throws Exception {
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        ResultSet rs = mock(ResultSet.class);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery("show databases")).thenReturn(rs);
        when(rs.next()).thenReturn(true, true, true, false);
        when(rs.getString(1)).thenReturn("default", "information_schema", "analytics");

        List<DatabaseItem> databases = catalogs.listDatabases(connection, null, false);

        assertThat(databases).extracting(DatabaseItem::getName).containsExactly("analytics", "default");
        assertThat(databases).extracting(DatabaseItem::getKind).containsOnly("NAMESPACE");
        verify(statement).executeQuery("show databases");
    }

    @Test void filtersDatabasesByKeyword() throws Exception {
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        ResultSet rs = mock(ResultSet.class);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery("show databases")).thenReturn(rs);
        when(rs.next()).thenReturn(true, true, false);
        when(rs.getString(1)).thenReturn("analytics", "sales");

        assertThat(catalogs.listDatabases(connection, "aly", false))
            .extracting(DatabaseItem::getName).containsExactly("analytics");
    }

    @Test void listsTablesAfterUseDatabase() throws Exception {
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        ResultSet rs = mock(ResultSet.class);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery("show tables")).thenReturn(rs);
        when(rs.next()).thenReturn(true, true, false);
        when(rs.getString(1)).thenReturn("orders", "customers");

        List<TableItem> tables = catalogs.listTables(connection, "analytics", null, new String[]{"TABLE"});

        assertThat(tables).extracting(TableItem::getName).containsExactly("customers", "orders");
        assertThat(tables).extracting(TableItem::getType).containsOnly("TABLE");
        assertThat(tables).extracting(TableItem::getDatabase).containsOnly("analytics");
        verify(statement).execute("use `analytics`");
        verify(statement).executeQuery("show tables");
    }

    @Test void listsViewsWithShowViewsWhenOnlyViewTypeRequested() throws Exception {
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        ResultSet rs = mock(ResultSet.class);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery("show views")).thenReturn(rs);
        when(rs.next()).thenReturn(true, false);
        when(rs.getString(1)).thenReturn("order_summary");

        List<TableItem> views = catalogs.listTables(connection, "analytics", null, new String[]{"VIEW"});

        assertThat(views).extracting(TableItem::getType).containsOnly("VIEW");
        verify(statement).executeQuery("show views");
    }

    @Test void reportsDatabaseNotFoundWhenUseFails() throws Exception {
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.execute("use `missing`")).thenThrow(new SQLException("Database does not exist: missing"));

        assertThatThrownBy(() -> catalogs.listTables(connection, "missing", null, new String[]{"TABLE"}))
            .isInstanceOf(ApiException.class).extracting("code").isEqualTo("DATABASE_NOT_FOUND");
    }

    @Test void readsTableDetailFromDescAndShowCreateTable() throws Exception {
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        ResultSet desc = mock(ResultSet.class);
        ResultSet ddl = mock(ResultSet.class);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery("desc `orders`")).thenReturn(desc);
        when(statement.executeQuery("show create table `orders`")).thenReturn(ddl);
        when(desc.next()).thenReturn(true, true, true, true, true, false);
        when(desc.getString(1)).thenReturn("id", "amount", "created_at", "", "# Partition Information", "dt");
        when(desc.getString(2)).thenReturn("bigint", "decimal(10,2)", "timestamp", "", "", "string");
        when(desc.getString(3)).thenReturn("主键", null, "", "", "", "分区列");
        when(ddl.next()).thenReturn(true, false);
        when(ddl.getString(2)).thenReturn("CREATE TABLE `orders` (id bigint)");

        TableDetailResponse detail = catalogs.tableDetail(connection, "analytics", "orders");

        assertThat(detail.getDatabase()).isEqualTo("analytics");
        assertThat(detail.getTable()).isEqualTo("orders");
        assertThat(detail.getColumns()).extracting(ColumnItem::getName).containsExactly("id", "amount", "created_at");
        assertThat(detail.getColumns().get(0).getJdbcType()).isEqualTo("BIGINT");
        assertThat(detail.getColumns().get(0).getTypeName()).isEqualTo("bigint");
        assertThat(detail.getColumns().get(0).getComment()).isEqualTo("主键");
        assertThat(detail.getColumns().get(1).getTypeName()).isEqualTo("decimal(10,2)");
        assertThat(detail.getColumns().get(1).getJdbcType()).isEqualTo("DECIMAL");
        assertThat(detail.getPrimaryKey()).isNull();
        assertThat(detail.getIndexes()).isEmpty();
        assertThat(detail.getDdl()).isEqualTo("CREATE TABLE `orders` (id bigint)");
        verify(statement).execute("use `analytics`");
    }

    @Test void rejectsMissingTableWhenDescReturnsNoColumns() throws Exception {
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        ResultSet desc = mock(ResultSet.class);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery("desc `ghost`")).thenReturn(desc);
        when(desc.next()).thenReturn(false);

        assertThatThrownBy(() -> catalogs.tableDetail(connection, "analytics", "ghost"))
            .isInstanceOf(ApiException.class).extracting("code").isEqualTo("TABLE_NOT_FOUND");
    }
}
