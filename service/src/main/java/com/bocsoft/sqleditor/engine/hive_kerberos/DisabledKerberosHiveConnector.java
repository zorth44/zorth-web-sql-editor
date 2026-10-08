package com.bocsoft.sqleditor.engine.hive_kerberos;

import com.bocsoft.sqleditor.engine.ConnectionFailure;
import java.sql.Connection;
import java.sql.SQLException;

public final class DisabledKerberosHiveConnector implements KerberosHiveConnector {
    @Override public Connection openConnection(KerberosConnectionSpec spec) throws SQLException {
        throw new SQLException("Kerberos connection components are unavailable");
    }

    @Override public ConnectionFailure classify(Throwable failure) {
        return new ConnectionFailure("CONNECTION_FAILED", "Kerberos 连接组件不可用或服务端未启用");
    }
}
