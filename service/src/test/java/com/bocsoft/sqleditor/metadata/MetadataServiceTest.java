package com.bocsoft.sqleditor.metadata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
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
        verify(targets).require(auth, "ds");
        verify(connection, never()).setCatalog(any());
        verify(connection).close();
    }
}
