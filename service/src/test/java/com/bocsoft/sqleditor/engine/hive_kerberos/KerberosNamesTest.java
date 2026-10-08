package com.bocsoft.sqleditor.engine.hive_kerberos;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class KerberosNamesTest {
    @Test void buildsServiceDiscoveryUrlWithoutQueue() {
        String url = KerberosNames.serviceDiscoveryUrl(
            "zk1.internal:2181,zk2.internal:2181", "sales", "hiveserver2",
            "hadoop/_HOST@TBDS-MYCV0F0C", null);
        assertThat(url).isEqualTo(
            "jdbc:hive2://zk1.internal:2181,zk2.internal:2181/sales"
                + ";serviceDiscoveryMode=zooKeeper;zooKeeperNamespace=hiveserver2"
                + ";principal=hadoop/_HOST@TBDS-MYCV0F0C");
        assertThat(url).doesNotContain("?tez.queue.name=");
    }

    @Test void appendsQueueAndFallsBackToDefaultNamespaceAndDatabase() {
        String url = KerberosNames.serviceDiscoveryUrl(
            "zk1.internal:2181", null, null, "hadoop/_HOST@REALM", "etl");
        assertThat(url).contains("/default").contains(";zooKeeperNamespace=hiveserver2")
            .endsWith(";principal=hadoop/_HOST@REALM?tez.queue.name=etl");
    }

    @Test void derivesLoginPrincipalFromKeytabFileName() {
        assertThat(KerberosNames.loginPrincipal("/opt/keytabs/hive.keytab", "REALM")).isEqualTo("hive@REALM");
        assertThat(KerberosNames.loginPrincipal("hive512", "REALM")).isEqualTo("hive512@REALM");
        assertThat(KerberosNames.loginPrincipal("hive.keytab", null)).isNull();
    }

    @Test void derivesUrlPrincipalFromTemplate() {
        assertThat(KerberosNames.urlPrincipal(null, "REALM")).isEqualTo("hadoop/_HOST@REALM");
        assertThat(KerberosNames.urlPrincipal("hive/_HOST@{realm}", "REALM")).isEqualTo("hive/_HOST@REALM");
        assertThat(KerberosNames.urlPrincipal("hadoop/_HOST@{realm}", null)).isNull();
    }

    @Test void derivesZookeeperPrincipal() {
        assertThat(KerberosNames.zookeeperPrincipal("TBDS-MYCV0F0C")).isEqualTo("zookeeper/hadoop.tbds-mycv0f0c");
        assertThat(KerberosNames.zookeeperPrincipal(null)).isEqualTo("zookeeper/hadoop");
    }

    @Test void buildsKeytabPathUnderEngineSubdirectory() {
        assertThat(KerberosNames.keytabPath("/opt/keytab", "hive531", "hive.keytab"))
            .isEqualTo("/opt/keytab/hive531/hive.keytab");
        assertThat(KerberosNames.keytabPath("/opt/keytab/", null, "hive.keytab"))
            .isEqualTo("/opt/keytab/hive.keytab");
        assertThat(KerberosNames.keytabPath(null, "hive531", "hive.keytab")).isNull();
    }
}
