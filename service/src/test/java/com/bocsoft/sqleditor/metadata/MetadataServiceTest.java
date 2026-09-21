package com.bocsoft.sqleditor.metadata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bocsoft.sqleditor.auth.AuthContext;
import com.bocsoft.sqleditor.common.ApiException;
import com.bocsoft.sqleditor.common.SqlEditorMetrics;
import com.bocsoft.sqleditor.config.SqlEditorProperties;
import com.bocsoft.sqleditor.datasource.SavedDataSource;
import com.bocsoft.sqleditor.datasource.TargetConnectionProvider;
import com.bocsoft.sqleditor.datasource.api.CursorPage;
import com.bocsoft.sqleditor.datasource.connection.ConnectionConfiguration;
import com.bocsoft.sqleditor.engine.EngineSupport;
import com.bocsoft.sqleditor.engine.mysql.MysqlEngineSupport;
import com.bocsoft.sqleditor.metadata.api.DatabaseItem;
import com.bocsoft.sqleditor.metadata.api.RelationshipEdge;
import com.bocsoft.sqleditor.metadata.api.RelationshipPage;
import com.bocsoft.sqleditor.metadata.api.TableItem;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.Arrays;
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
        MetadataService service = service(targets, p);
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
        MetadataService service = service(targets, p);
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
        MetadataService service = service(targets, p);
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

    @Test
    void relationshipsAuthorizeBeforeBorrowAndRejectInvalidInput() throws Exception {
        TargetConnectionProvider targets = mock(TargetConnectionProvider.class);
        AuthContext auth = new AuthContext("u", "user", "User", "product-a", "A", Instant.now().plusSeconds(60));
        SqlEditorProperties p = new SqlEditorProperties();
        p.getCursor().setSigningKey(Base64.getEncoder().encodeToString(new byte[32]));
        MetadataService service = service(targets, p);
        when(targets.require(auth, "ds")).thenThrow(ApiException.notFound());
        assertThatThrownBy(() -> service.relationships(auth, "ds", "sales", "orders", "BOTH", 10, null))
            .isInstanceOf(ApiException.class)
            .extracting("code").isEqualTo("DATA_SOURCE_NOT_FOUND");
        verify(targets, never()).borrow(any());

        SavedDataSource source = new SavedDataSource("ds", "Orders", 1, null, new ConnectionConfiguration("db", 3306, "u", "secret", null, "DISABLED", 10, Collections.emptyMap()));
        MysqlEngineSupport mysql = new MysqlEngineSupport();
        when(targets.require(auth, "ok")).thenReturn(source);
        when(targets.engine(source)).thenReturn(mysql);
        assertThatThrownBy(() -> service.relationships(auth, "ok", "sales", "orders", "SIDEWAYS", 10, null))
            .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.relationships(auth, "ok", "sales", "orders", "BOTH", 0, null))
            .isInstanceOf(ApiException.class);
        verify(targets, never()).borrow(source);
    }

    @Test
    void gbaseRelationshipsAreUnsupportedWithoutBorrowing() throws Exception {
        TargetConnectionProvider targets = mock(TargetConnectionProvider.class);
        AuthContext auth = new AuthContext("u", "user", "User", "product-a", "A", Instant.now().plusSeconds(60));
        SavedDataSource source = new SavedDataSource("ds", "G", 1, null, new ConnectionConfiguration("GBASE_8A", "db", 5258, "u", "secret", null, "DISABLED", 10, Collections.emptyMap()));
        when(targets.require(auth, "ds")).thenReturn(source);
        when(targets.engine(source)).thenReturn(new com.bocsoft.sqleditor.engine.gbase8a.Gbase8aEngineSupport(new MysqlEngineSupport()));
        SqlEditorProperties p = new SqlEditorProperties();
        p.getCursor().setSigningKey(Base64.getEncoder().encodeToString(new byte[32]));
        MetadataService service = service(targets, p);
        assertThatThrownBy(() -> service.relationships(auth, "ds", "sales", "orders", "BOTH", 10, null))
            .isInstanceOf(ApiException.class)
            .extracting("code").isEqualTo("CAPABILITY_NOT_SUPPORTED");
        verify(targets, never()).borrow(any());
    }

    @Test
    void pagesCompositeEdgesAtomicallyAndHonorsProviderCap() throws Exception {
        TargetConnectionProvider targets = mock(TargetConnectionProvider.class);
        Connection connection = mock(Connection.class);
        AuthContext auth = new AuthContext("u", "user", "User", "product-a", "A", Instant.now().plusSeconds(60));
        SavedDataSource source = new SavedDataSource("ds", "Orders", 1, "sales",
            new ConnectionConfiguration("MYSQL", "db", 3306, "u", "secret", "sales", "DISABLED", 10, Collections.emptyMap()));
        EngineSupport engine = mock(EngineSupport.class);
        when(targets.require(auth, "ds")).thenReturn(source);
        when(targets.engine(source)).thenReturn(engine);
        when(targets.borrow(source)).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true);
        when(engine.supportsRelationshipMetadata()).thenReturn(true);
        doNothing().when(engine).validateIdentifier(anyString(), anyString());
        when(engine.tableDetail(eq(connection), eq("sales"), eq("child"))).thenReturn(
            new com.bocsoft.sqleditor.metadata.api.TableDetailResponse("sales", "child",
                Collections.<com.bocsoft.sqleditor.metadata.api.ColumnItem>emptyList(),
                new com.bocsoft.sqleditor.metadata.api.PrimaryKeyItem("PRIMARY", Collections.singletonList("id")),
                Collections.<com.bocsoft.sqleditor.metadata.api.IndexItem>emptyList(), null, null));
        when(engine.tableConstraints(eq(connection), eq("sales"), eq("child"), any()))
            .thenReturn(com.bocsoft.sqleditor.metadata.api.TableConstraintMetadata.unavailable());
        RelationshipEdge composite = new RelationshipEdge("sales", "child", Arrays.asList("a", "b"),
            "sales", "alpha", Arrays.asList("id", "code"), "OUTBOUND", "fk_a", "FOREIGN_KEY", "MANY_TO_ONE");
        RelationshipEdge second = new RelationshipEdge("sales", "child", Collections.singletonList("c"),
            "sales", "bravo", Collections.singletonList("id"), "OUTBOUND", "fk_b", "FOREIGN_KEY", "MANY_TO_ONE");
        when(engine.importedRelationships(eq(connection), eq("sales"), eq("child"), any(), anyInt()))
            .thenReturn(Arrays.asList(composite, second));
        when(engine.exportedRelationships(eq(connection), eq("sales"), eq("child"), any(), anyInt()))
            .thenReturn(Collections.<RelationshipEdge>emptyList());
        SqlEditorProperties p = new SqlEditorProperties();
        p.getCursor().setSigningKey(Base64.getEncoder().encodeToString(new byte[32]));
        p.getAgentApi().setMaxRelationshipsPerPage(1);
        MetadataService service = service(targets, p);
        RelationshipPage first = service.relationships(auth, "ds", "sales", "child", "OUTBOUND", 1, null);
        assertThat(first.getItems()).hasSize(1);
        assertThat(first.getItems().get(0).getSourceColumns()).containsExactly("a", "b");
        assertThat(first.getCoverage()).isEqualTo("TRUNCATED");
        assertThat(first.getNextPageToken()).isNotBlank();
        RelationshipPage secondPage = service.relationships(auth, "ds", "sales", "child", "OUTBOUND", 1, first.getNextPageToken());
        assertThat(secondPage.getItems()).extracting(RelationshipEdge::getConstraintName).containsExactly("fk_b");
        assertThat(secondPage.getItems().get(0).getSourceColumns()).containsExactly("c");
        verify(connection, org.mockito.Mockito.atLeastOnce()).close();
        assertThatThrownBy(() -> service.relationships(auth, "ds", "sales", "child", "OUTBOUND", 2, null))
            .isInstanceOf(ApiException.class);
    }

    private MetadataService service(TargetConnectionProvider targets, SqlEditorProperties properties) {
        return new MetadataService(targets, new MetadataCursorCodec(new ObjectMapper(), properties),
            new RelationshipCursorCodec(new ObjectMapper(), properties), properties,
            new SqlEditorMetrics(new SimpleMeterRegistry()));
    }
}
