package com.bocsoft.sqleditor.engine.hive_kerberos;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

public final class KerberosLoginWindow {
    public interface KerberosLogin {
        void login(KerberosConnectionSpec spec) throws Exception;
        Connection connect(String jdbcUrl) throws SQLException;
        void reset();
    }

    private static final String[] SNAPSHOT_KEYS = {
        "java.security.krb5.conf",
        "java.security.krb5.realm",
        "java.security.krb5.kdc",
        "javax.security.auth.useSubjectCredsOnly",
        "zookeeper.server.principal",
        "SERVER_REALM"
    };

    private final ReentrantLock lock = new ReentrantLock();
    private final KerberosLogin login;

    public KerberosLoginWindow(KerberosLogin login) {
        this.login = login;
    }

    public Connection run(KerberosConnectionSpec spec) throws SQLException {
        lock.lock();
        Map<String, String> snapshot = snapshot();
        try {
            login.login(spec);
            return login.connect(spec.getJdbcUrl());
        } catch (SQLException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new SQLException("Service-discovery connection failed", failure);
        } finally {
            try {
                login.reset();
            } catch (RuntimeException ignored) {
                // Reset must never mask the original failure.
            } finally {
                restore(snapshot);
                lock.unlock();
            }
        }
    }

    static Map<String, String> snapshot() {
        Map<String, String> values = new LinkedHashMap<String, String>();
        for (String key : SNAPSHOT_KEYS) values.put(key, System.getProperty(key));
        return values;
    }

    static void restore(Map<String, String> snapshot) {
        for (Map.Entry<String, String> entry : snapshot.entrySet()) {
            if (entry.getValue() == null) System.clearProperty(entry.getKey());
            else System.setProperty(entry.getKey(), entry.getValue());
        }
    }
}
