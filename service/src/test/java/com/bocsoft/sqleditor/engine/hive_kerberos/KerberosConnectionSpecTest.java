package com.bocsoft.sqleditor.engine.hive_kerberos;

import static org.assertj.core.api.Assertions.assertThat;

import com.bocsoft.sqleditor.config.SqlEditorProperties;
import com.bocsoft.sqleditor.datasource.connection.ConnectionConfiguration;
import com.bocsoft.sqleditor.datasource.connection.JdbcTarget;
import java.util.Collections;
import org.junit.jupiter.api.Test;

class KerberosConnectionSpecTest {
    private static SqlEditorProperties.Kerberos kerberos() {
        SqlEditorProperties.Kerberos kerberos = new SqlEditorProperties.Kerberos();
        kerberos.setKeytabBasePath("/opt/keytab");
        SqlEditorProperties.Kerberos.Environment env = new SqlEditorProperties.Kerberos.Environment();
        env.setZookeeperQuorum("zk.internal:2181");
        env.setKrb5Conf("/etc/krb5.conf");
        env.setRealm("REALM");
        kerberos.getEnvironments().put("prod", env);
        return kerberos;
    }

    private static ConnectionConfiguration configuration() {
        return new ConnectionConfiguration("HIVE_KERBEROS", null, 0, null, null, "sales", null, 10,
            Collections.<String, String>emptyMap(), "prod", "hive.keytab", "etl");
    }

    @Test void resolvesEnvironmentIntoServicesTarget() {
        KerberosConnectionSpec spec = KerberosConnectionSpec.forConfiguration(configuration(), kerberos(), "hive531");
        assertThat(spec.isResolved()).isTrue();
        assertThat(spec.getKeytabPath()).isEqualTo("/opt/keytab/hive531/hive.keytab");
        assertThat(spec.getLoginPrincipal()).isEqualTo("hive@REALM");
        assertThat(spec.getJdbcUrl()).contains(";principal=hadoop/_HOST@REALM?tez.queue.name=etl");

        JdbcTarget target = spec.toJdbcTarget();
        assertThat(target.getUrls()).hasSize(1);
        KerberosConnectionSpec decoded = KerberosConnectionSpec.fromJdbcTarget(target);
        assertThat(decoded.isResolved()).isTrue();
        assertThat(decoded.getEnvironment()).isEqualTo("prod");
        assertThat(decoded.getJdbcUrl()).isEqualTo(spec.getJdbcUrl());
    }

    @Test void unknownEnvironmentIsUnresolvedButStillBuildsTarget() {
        ConnectionConfiguration configuration = new ConnectionConfiguration("HIVE_KERBEROS", null, 0, null, null,
            "sales", null, 10, Collections.<String, String>emptyMap(), "nope", "hive.keytab", null);
        KerberosConnectionSpec spec = KerberosConnectionSpec.forConfiguration(configuration, kerberos(), "hive531");
        assertThat(spec.isResolved()).isFalse();
        assertThat(spec.toJdbcTarget().getUrls().get(0)).isEqualTo("jdbc:hive2://");
        assertThat(KerberosConnectionSpec.fromJdbcTarget(spec.toJdbcTarget()).isResolved()).isFalse();
    }

    @Test void absentConfigurationLeavesSpecUnresolved() {
        SqlEditorProperties.Kerberos disabled = new SqlEditorProperties.Kerberos();
        KerberosConnectionSpec spec = KerberosConnectionSpec.forConfiguration(configuration(), disabled, "hive531");
        assertThat(spec.isResolved()).isFalse();
    }
}
