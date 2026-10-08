package com.bocsoft.sqleditor.engine.hive_kerberos.vendor;

import com.bocsoft.sqleditor.engine.hive_kerberos.KerberosConnectionSpec;
import com.bocsoft.sqleditor.engine.hive_kerberos.KerberosLoginWindow;
import com.bocsoft.sqleditor.engine.hive_kerberos.KerberosNames;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.security.UserGroupInformation;

public class TbdsKerberosHiveConnector implements KerberosLoginWindow.KerberosLogin {
    private static final String HIVE_DRIVER = "org.apache.hive.jdbc.HiveDriver";

    @Override public void login(KerberosConnectionSpec spec) throws Exception {
        System.setProperty("java.security.krb5.conf", spec.getKrb5Conf());
        System.setProperty("javax.security.auth.useSubjectCredsOnly", "false");
        KerberosUtil.refreshKerberosConfig();

        Configuration configuration = new Configuration();
        configuration.set("hadoop.security.authentication", "Kerberos");
        UserGroupInformation.setConfiguration(configuration);

        KerberosUtil.setDefaultRealm(spec.getRealm());
        UserGroupInformation.loginUserFromKeytab(spec.getLoginPrincipal(), spec.getKeytabPath());

        System.setProperty("zookeeper.server.principal", KerberosNames.zookeeperPrincipal(spec.getRealm()));
        Class.forName(HIVE_DRIVER);
    }

    @Override public Connection connect(String jdbcUrl) throws SQLException {
        return DriverManager.getConnection(jdbcUrl, "", "");
    }

    @Override public void reset() {
        try {
            UserGroupInformation.reset();
        } catch (Throwable ignored) {
            // Resetting global Kerberos state must never mask the connection outcome.
        }
    }
}
