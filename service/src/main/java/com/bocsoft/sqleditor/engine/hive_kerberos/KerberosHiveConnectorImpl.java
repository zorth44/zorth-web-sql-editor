package com.bocsoft.sqleditor.engine.hive_kerberos;

import com.bocsoft.sqleditor.datasource.connection.NetworkPolicy;
import com.bocsoft.sqleditor.engine.ConnectionFailure;
import java.sql.Connection;
import java.sql.SQLException;

public final class KerberosHiveConnectorImpl implements KerberosHiveConnector {
    private final NetworkPolicy networkPolicy;
    private final KerberosLoginWindow window;
    private final KerberosFailureClassifier failures = new KerberosFailureClassifier();

    public KerberosHiveConnectorImpl(NetworkPolicy networkPolicy, KerberosLoginWindow window) {
        this.networkPolicy = networkPolicy;
        this.window = window;
    }

    @Override public Connection openConnection(KerberosConnectionSpec spec) throws SQLException {
        if (spec == null || !spec.isResolved()) {
            throw new SQLException("The environment is not configured on the server");
        }
        KerberosEndpoints.assertAllowed(networkPolicy, spec.getZkQuorum());
        return window.run(spec);
    }

    @Override public ConnectionFailure classify(Throwable failure) {
        return failures.classify(failure);
    }
}
