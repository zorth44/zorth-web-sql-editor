package com.bocsoft.sqleditor.metadata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bocsoft.sqleditor.auth.AuthContext;
import com.bocsoft.sqleditor.config.SqlEditorProperties;
import com.bocsoft.sqleditor.datasource.SavedDataSource;
import com.bocsoft.sqleditor.datasource.TargetConnectionProvider;
import com.bocsoft.sqleditor.datasource.api.CursorPage;
import com.bocsoft.sqleditor.datasource.connection.ConnectionConfiguration;
import com.bocsoft.sqleditor.engine.mysql.MysqlEngineSupport;
import com.bocsoft.sqleditor.metadata.api.DatabaseItem;
import com.bocsoft.sqleditor.metadata.api.TableItem;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.Base64;
import java.util.Collections;
import org.junit.jupiter.api.Test;

class MetadataServiceTest {
    @Test
    void hidesSystemCatalogsAndUsesProductScopedTarget() throws Exception {
        TargetConnectionProvider targets = mock(TargetConnectionProvider.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        ResultSet catalogs = mock(ResultSet.class);
        AuthContext auth = new AuthContext("u", "user", "User", "product-a", "A", Instant.now().plusSeconds(60));
        SavedDataSource source = new SavedDataSource("ds", "Orders", 1, null, new ConnectionConfiguration("db", 3306, "u", "secret", null, "DISABLED", 10, Collections.emptyMap()));
        MysqlEngineSupport mysql = new MysqlEngineSupport();
        when(targets.require(auth, "ds")).thenReturn(source);
        when(targets.engine(source)).thenReturn(mysql);
        when(targets.borrow(source)).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metadata);
        when(connection.getAutoCommit()).thenReturn(true);
        when(metadata.getCatalogs()).thenReturn(catalogs);
        when(catalogs.next()).thenReturn(true, true, false);
        when(catalogs.getString(1)).thenReturn("orders", "information_schema");
        SqlEditorProperties p = new SqlEditorProperties();
        p.getCursor().setSigningKey(Base64.getEncoder().encodeToString(new byte[32]));
        MetadataService service = new MetadataService(targets, new MetadataCursorCodec(new ObjectMapper(), p));
        CursorPage<DatabaseItem> page = service.databases(auth, "ds", "", 100, null, false);
        assertThat(page.getItems()).extracting(DatabaseItem::getName).containsExactly("orders");
        assertThat(page.getItems()).extracting(DatabaseItem::getKind).containsExactly("NAMESPACE");
        verify(targets).require(auth, "ds");
        verify(connection, never()).setCatalog(any());
        verify(connection).close();
    }

    @Test
    void listsTablesBoundToParentNamespaceName() throws Exception {
        TargetConnectionProvider targets = mock(TargetConnectionProvider.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        ResultSet catalogs = mock(ResultSet.class);
        ResultSet tables = mock(ResultSet.class);
        AuthContext auth = new AuthContext("u", "user", "User", "product-a", "A", Instant.now().plusSeconds(60));
        SavedDataSource source = new SavedDataSource("ds", "Orders", 1, null, new ConnectionConfiguration("db", 3306, "u", "secret", null, "DISABLED", 10, Collections.emptyMap()));
        MysqlEngineSupport mysql = new MysqlEngineSupport();
        when(targets.require(auth, "ds")).thenReturn(source);
        when(targets.engine(source)).thenReturn(mysql);
        when(targets.borrow(source)).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metadata);
        when(connection.getAutoCommit()).thenReturn(true);
        when(metadata.getCatalogs()).thenReturn(catalogs);
        when(catalogs.next()).thenReturn(true, false);
        when(catalogs.getString(1)).thenReturn("orders");
        when(metadata.getTables(eq("orders"), isNull(), eq("%"), any(String[].class))).thenReturn(tables);
        when(tables.next()).thenReturn(true, false);
        when(tables.getString("TABLE_NAME")).thenReturn("order_item");
        when(tables.getString("TABLE_TYPE")).thenReturn("TABLE");
        when(tables.getString("REMARKS")).thenReturn("订单明细");
        SqlEditorProperties p = new SqlEditorProperties();
        p.getCursor().setSigningKey(Base64.getEncoder().encodeToString(new byte[32]));
        MetadataService service = new MetadataService(targets, new MetadataCursorCodec(new ObjectMapper(), p));
        CursorPage<TableItem> page = service.tables(auth, "ds", "orders", "", "TABLE,VIEW", 200, null);
        assertThat(page.getItems()).hasSize(1);
        assertThat(page.getItems().get(0).getDatabase()).isEqualTo("orders");
        assertThat(page.getItems().get(0).getName()).isEqualTo("order_item");
        assertThat(page.getItems().get(0).getType()).isEqualTo("TABLE");
    }

    @Test
    void listsPostgresSchemasAsNamespacesAndBindsTablesToSchema() throws Exception {
        TargetConnectionProvider targets = mock(TargetConnectionProvider.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        ResultSet schemas = mock(ResultSet.class);
        ResultSet tables = mock(ResultSet.class);
        java.sql.Statement restore = mock(java.sql.Statement.class);
        AuthContext auth = new AuthContext("u", "user", "User", "product-a", "A", Instant.now().plusSeconds(60));
        SavedDataSource source = new SavedDataSource("ds", "Orders", 1, "orders",
            new ConnectionConfiguration("POSTGRESQL", "db", 5432, "u", "secret", "orders", "DISABLED", 10, Collections.emptyMap()));
        com.bocsoft.sqleditor.engine.postgres.PostgresEngineSupport postgres =
            new com.bocsoft.sqleditor.engine.postgres.PostgresEngineSupport();
        when(targets.require(auth, "ds")).thenReturn(source);
        when(targets.engine(source)).thenReturn(postgres);
        when(targets.borrow(source)).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metadata);
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.createStatement()).thenReturn(restore);
        when(metadata.getSchemas()).thenReturn(schemas);
        when(schemas.next()).thenReturn(true, true, true, false, true, true, true, false);
        when(schemas.getString("TABLE_SCHEM")).thenReturn("public", "pg_catalog", "sales", "public", "pg_catalog", "sales");
        when(metadata.getTables(isNull(), eq("sales"), eq("%"), any(String[].class))).thenReturn(tables);
        when(tables.next()).thenReturn(true, false);
        when(tables.getString("TABLE_NAME")).thenReturn("order_item");
        when(tables.getString("TABLE_TYPE")).thenReturn("TABLE");
        when(tables.getString("REMARKS")).thenReturn(null);
        SqlEditorProperties p = new SqlEditorProperties();
        p.getCursor().setSigningKey(Base64.getEncoder().encodeToString(new byte[32]));
        MetadataService service = new MetadataService(targets, new MetadataCursorCodec(new ObjectMapper(), p));
        CursorPage<DatabaseItem> databases = service.databases(auth, "ds", "", 100, null, false);
        assertThat(databases.getItems()).extracting(DatabaseItem::getName).containsExactly("public", "sales");
        assertThat(databases.getItems()).extracting(DatabaseItem::getKind).containsExactly("NAMESPACE", "NAMESPACE");
        CursorPage<TableItem> page = service.tables(auth, "ds", "sales", "", "TABLE,VIEW", 200, null);
        assertThat(page.getItems()).hasSize(1);
        assertThat(page.getItems().get(0).getDatabase()).isEqualTo("sales");
        assertThat(page.getItems().get(0).getName()).isEqualTo("order_item");
        verify(connection).setSchema("sales");
        verify(connection, never()).setCatalog(any());
    }
}
