package com.bocsoft.sqleditor.engine.iceberg;

import static org.assertj.core.api.Assertions.assertThat;

import com.bocsoft.sqleditor.config.SqlEditorProperties;
import com.bocsoft.sqleditor.datasource.connection.ConnectionConfiguration;
import com.bocsoft.sqleditor.datasource.connection.JdbcTarget;
import com.bocsoft.sqleditor.datasource.connection.NetworkPolicy;
import com.bocsoft.sqleditor.engine.EngineField;
import com.bocsoft.sqleditor.engine.EngineId;
import com.bocsoft.sqleditor.engine.hive.HiveEngineSupport;
import com.bocsoft.sqleditor.engine.hive_kerberos.DisabledKerberosHiveConnector;
import com.bocsoft.sqleditor.engine.hive_kerberos.KerberosConnectionSpec;
import com.bocsoft.sqleditor.engine.hive_kerberos.KerberosHiveConnectorImpl;
import com.bocsoft.sqleditor.engine.hive_kerberos.KerberosLoginWindow;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class IcebergEngineTest {
    private static SqlEditorProperties properties() {
        SqlEditorProperties properties = new SqlEditorProperties();
        properties.getNetwork().setAllowedCidrs(Collections.singletonList("10.0.0.0/8"));
        SqlEditorProperties.Kerberos kerberos = properties.getKerberos();
        kerberos.setKeytabBasePath("/opt/keytab");
        SqlEditorProperties.Kerberos.Environment env = new SqlEditorProperties.Kerberos.Environment();
        env.setZookeeperQuorum("10.0.0.5:2181");
        env.setKrb5Conf("/etc/krb5.conf");
        env.setRealm("REALM");
        kerberos.getEnvironments().put("prod", env);
        return properties;
    }

    private static ConnectionConfiguration configuration() {
        return new ConnectionConfiguration(EngineId.ICEBERG, null, 0, null, null, "sales", null, 10,
            Collections.<String, String>emptyMap(), "prod", "iceberg.keytab", "etl");
    }

    @Test void declaresKerberosFamilyAndShortLivedAcquisition() {
        IcebergEngineSupport engine = new IcebergEngineSupport(
            new HiveEngineSupport(), new DisabledKerberosHiveConnector(), properties());
        assertThat(engine.id()).isEqualTo(EngineId.ICEBERG);
        assertThat(engine.family()).isEqualTo("HIVE_WIRE");
        assertThat(engine.defaultNamespaceRequired()).isFalse();
        assertThat(engine.canSwitchNamespaceOnConnection()).isTrue();
        assertThat(engine.identifierMaxLength()).isEqualTo(128);
        assertThat(engine.requiresHostResolution()).isFalse();
        assertThat(engine.usesPooledConnections()).isFalse();
    }

    @Test void descriptorDeclaresIcebergKerberosFieldsOnly() {
        IcebergEngineSupport engine = new IcebergEngineSupport(
            new HiveEngineSupport(), new DisabledKerberosHiveConnector(), properties());
        assertThat(engine.descriptor().getDisplayName()).isEqualTo("Iceberg");
        assertThat(engine.descriptor().getFamily()).isEqualTo("HIVE_WIRE");
        assertThat(engine.descriptor().getDefaultPort()).isEqualTo(10000);
        assertThat(engine.descriptor().getEditorLanguage()).isEqualTo("hive");
        assertThat(engine.descriptor().getIdentifierQuote()).isEqualTo("`");
        List<EngineField> fields = engine.descriptor().getConnectionFields();
        assertThat(fields).extracting(EngineField::getName)
            .containsExactly("environment", "keytabFile", "queueName", "defaultDatabase");
        assertThat(fields).extracting(EngineField::getName)
            .doesNotContain("host", "port", "username", "password", "sslMode");
        assertThat(fields.get(0).getKind()).isEqualTo("ENVIRONMENT");
        assertThat(fields.get(0).getWidget()).isEqualTo("SELECT");
        assertThat(fields.get(0).isRequired()).isTrue();
        assertThat(fields.get(0).getOptions()).extracting(o -> o.getValue()).containsExactly("prod");
        assertThat(fields.get(1).getKind()).isEqualTo("KEYTAB");
        assertThat(fields.get(1).isRequired()).isTrue();
        assertThat(fields.get(2).getKind()).isEqualTo("QUEUE");
        assertThat(fields.get(2).isRequired()).isFalse();
        assertThat(fields.get(3).getKind()).isEqualTo("DEFAULT_NAMESPACE");
    }

    @Test void disabledConnectorClassifiesAsSanitizedConnectionFailure() {
        IcebergEngineSupport engine = new IcebergEngineSupport(
            new HiveEngineSupport(), new DisabledKerberosHiveConnector(), properties());
        assertThat(engine.classifyConnectionFailure(new SQLException("/opt/keytab/iceberg/iceberg.keytab missing"))
            .getCode()).isEqualTo("CONNECTION_FAILED");
        assertThat(engine.classifyConnectionFailure(new SQLException("boom")).getMessage())
            .doesNotContain("/opt/keytab").doesNotContain("iceberg.keytab");
    }

    @Test void resolvesKeytabUnderIcebergSubdirectoryAndBuildsServiceDiscoveryUrl() {
        SqlEditorProperties properties = properties();
        IcebergEngineSupport engine = new IcebergEngineSupport(
            new HiveEngineSupport(), new DisabledKerberosHiveConnector(), properties);

        JdbcTarget target = engine.buildJdbc(configuration(), null);
        KerberosConnectionSpec spec = KerberosConnectionSpec.fromJdbcTarget(target);
        assertThat(spec.getKeytabSubdirectory()).isEqualTo("iceberg");
        assertThat(spec.getKeytabPath()).isEqualTo("/opt/keytab/iceberg/iceberg.keytab");
        assertThat(target.getUrls().get(0)).startsWith("jdbc:hive2://10.0.0.5:2181/sales")
            .contains("serviceDiscoveryMode=zooKeeper")
            .contains("principal=hadoop/_HOST@REALM")
            .contains("?tez.queue.name=etl");
    }

    @Test void buildsResolvedJdbcTargetAndConnectsThroughConnector() throws Exception {
        SqlEditorProperties properties = properties();
        NetworkPolicy policy = new NetworkPolicy(properties);
        AtomicReference<String> connectedUrl = new AtomicReference<String>();
        KerberosLoginWindow window = new KerberosLoginWindow(new KerberosLoginWindow.KerberosLogin() {
            @Override public void login(KerberosConnectionSpec spec) { /* no KDC in unit test */ }
            @Override public Connection connect(String jdbcUrl) { connectedUrl.set(jdbcUrl); return null; }
            @Override public void reset() { }
        });
        IcebergEngineSupport engine = new IcebergEngineSupport(
            new HiveEngineSupport(), new KerberosHiveConnectorImpl(policy, window), properties);

        JdbcTarget target = engine.buildJdbc(configuration(), null);
        engine.openConnection(target);
        assertThat(connectedUrl.get()).isEqualTo(target.getUrls().get(0));
    }

    @Test void connectorRejectsOutOfRangeQuorumBeforeLogin() {
        SqlEditorProperties properties = properties();
        SqlEditorProperties.Kerberos.Environment env = properties.getKerberos().getEnvironments().get("prod");
        env.setZookeeperQuorum("192.168.1.1:2181");
        IcebergEngineSupport engine = new IcebergEngineSupport(new HiveEngineSupport(),
            new KerberosHiveConnectorImpl(new NetworkPolicy(properties), new KerberosLoginWindow(
                new KerberosLoginWindow.KerberosLogin() {
                    @Override public void login(KerberosConnectionSpec spec) { throw new AssertionError("must not login"); }
                    @Override public Connection connect(String jdbcUrl) { throw new AssertionError("must not connect"); }
                    @Override public void reset() { }
                })), properties);
        JdbcTarget target = engine.buildJdbc(configuration(), null);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> engine.openConnection(target))
            .isInstanceOf(com.bocsoft.sqleditor.common.ApiException.class);
    }
}
