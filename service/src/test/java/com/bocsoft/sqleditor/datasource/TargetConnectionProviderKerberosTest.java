package com.bocsoft.sqleditor.datasource;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bocsoft.sqleditor.config.SqlEditorProperties;
import com.bocsoft.sqleditor.datasource.connection.ConnectionConfiguration;
import com.bocsoft.sqleditor.datasource.connection.DynamicPoolManager;
import com.bocsoft.sqleditor.datasource.connection.JdbcConfigurationBuilder;
import com.bocsoft.sqleditor.datasource.connection.NetworkPolicy;
import com.bocsoft.sqleditor.datasource.connection.ShortLivedConnectionTester;
import com.bocsoft.sqleditor.engine.EngineId;
import com.bocsoft.sqleditor.engine.EngineRegistry;
import com.bocsoft.sqleditor.engine.EngineSupport;
import com.bocsoft.sqleditor.engine.hive.HiveEngineSupport;
import com.bocsoft.sqleditor.engine.hive_kerberos.DisabledKerberosHiveConnector;
import com.bocsoft.sqleditor.engine.hive_kerberos.HiveKerberosEngineSupport;
import com.bocsoft.sqleditor.engine.mysql.MysqlEngineSupport;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;

class TargetConnectionProviderKerberosTest {
    private final SqlEditorProperties properties = new SqlEditorProperties();
    {
        properties.getNetwork().setAllowedCidrs(Collections.singletonList("10.0.0.0/8"));
    }

    private EngineRegistry engines() {
        return new EngineRegistry(Arrays.<EngineSupport>asList(
            new MysqlEngineSupport(), new HiveEngineSupport(),
            new HiveKerberosEngineSupport(new DisabledKerberosHiveConnector(), properties)));
    }

    private TargetConnectionProvider provider(DynamicPoolManager pools) {
        EngineRegistry engines = engines();
        JdbcConfigurationBuilder builder = new JdbcConfigurationBuilder(new NetworkPolicy(properties), engines);
        return new TargetConnectionProvider(mock(DataSourceService.class), pools, engines, builder);
    }

    @Test void kerberosConnectionTestFailsClosedWithSanitizedConnectionFailed() {
        EngineRegistry engines = engines();
        JdbcConfigurationBuilder builder = new JdbcConfigurationBuilder(new NetworkPolicy(properties), engines);
        ShortLivedConnectionTester tester = new ShortLivedConnectionTester(builder, engines);
        ConnectionConfiguration configuration = new ConnectionConfiguration(EngineId.HIVE_KERBEROS, null, 0, null,
            null, "sales", null, 10, Collections.<String, String>emptyMap(), "prod", "hive.keytab", null);
        com.bocsoft.sqleditor.datasource.api.ConnectionTestResult result = tester.test(configuration);
        org.assertj.core.api.Assertions.assertThat(result.getStatus()).isEqualTo("FAILED");
        org.assertj.core.api.Assertions.assertThat(result.getFailureCode()).isEqualTo("CONNECTION_FAILED");
        org.assertj.core.api.Assertions.assertThat(result.getMessage())
            .doesNotContain("keytab").doesNotContain("/opt").doesNotContain("hive.keytab");
    }

    private static SavedDataSource kerberosSource() {
        ConnectionConfiguration configuration = new ConnectionConfiguration(EngineId.HIVE_KERBEROS, null, 0, null, null,
            "sales", null, 10, Collections.<String, String>emptyMap(), "prod", "hive.keytab", null);
        return new SavedDataSource("ds-krb", "Kerberos", 1, null, configuration);
    }

    private static SavedDataSource mysqlSource() {
        ConnectionConfiguration configuration = new ConnectionConfiguration(EngineId.MYSQL, "db.internal", 3306,
            "user", "secret", "orders", "DISABLED", 10, Collections.<String, String>emptyMap());
        return new SavedDataSource("ds-mysql", "MySQL", 1, "orders", configuration);
    }

    @Test void pooledEngineBorrowsFromDynamicPool() throws Exception {
        DynamicPoolManager pools = mock(DynamicPoolManager.class);
        Connection connection = mock(Connection.class);
        SavedDataSource source = mysqlSource();
        when(pools.borrow(source.getId(), source.getVersion(), source.getConfiguration())).thenReturn(connection);
        TargetConnectionProvider provider = provider(pools);
        org.assertj.core.api.Assertions.assertThat(provider.borrow(source)).isSameAs(connection);
        verify(pools).borrow(source.getId(), source.getVersion(), source.getConfiguration());
    }

    @Test void kerberosEngineBypassesPoolAndFailsClosedWithoutVendor() {
        DynamicPoolManager pools = mock(DynamicPoolManager.class);
        TargetConnectionProvider provider = provider(pools);
        assertThatThrownBy(() -> provider.borrow(kerberosSource())).isInstanceOf(SQLException.class);
        verifyNoInteractions(pools);
    }

    @Test void kerberosEvictorClosesInsteadOfReturningToPool() throws Exception {
        DynamicPoolManager pools = mock(DynamicPoolManager.class);
        Connection connection = mock(Connection.class);
        when(connection.isClosed()).thenReturn(false);
        TargetConnectionProvider provider = provider(pools);
        provider.evictor(kerberosSource()).evict(connection);
        verify(connection).close();
        verifyNoInteractions(pools);
    }
}
