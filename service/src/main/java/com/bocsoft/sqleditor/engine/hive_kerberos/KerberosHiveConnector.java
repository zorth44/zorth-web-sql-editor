package com.bocsoft.sqleditor.engine.hive_kerberos;

import com.bocsoft.sqleditor.engine.ConnectionFailure;
import java.sql.Connection;
import java.sql.SQLException;

public interface KerberosHiveConnector {
    Connection openConnection(KerberosConnectionSpec spec) throws SQLException;

    ConnectionFailure classify(Throwable failure);
}
