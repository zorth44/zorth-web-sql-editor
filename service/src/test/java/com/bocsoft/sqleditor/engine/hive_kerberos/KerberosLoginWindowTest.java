package com.bocsoft.sqleditor.engine.hive_kerberos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class KerberosLoginWindowTest {
    private static final String KRB5_KEY = "java.security.krb5.conf";
    private static final String ZK_KEY = "zookeeper.server.principal";

    @AfterEach void clear() {
        System.clearProperty(KRB5_KEY);
        System.clearProperty(ZK_KEY);
    }

    private static KerberosConnectionSpec spec() {
        return new KerberosConnectionSpec("prod", "hive.keytab", null, "sales", 10,
            "/opt/keytab", "hive531", "/etc/krb5.conf", "REALM", "zk.internal:2181", "hiveserver2",
            "hadoop/_HOST@{realm}");
    }

    @Test void restoresGlobalStateAfterSuccess() throws Exception {
        System.setProperty(KRB5_KEY, "/original/krb5.conf");
        System.setProperty(ZK_KEY, "zookeeper/original");
        AtomicInteger resets = new AtomicInteger();
        KerberosLoginWindow window = new KerberosLoginWindow(new KerberosLoginWindow.KerberosLogin() {
            @Override public void login(KerberosConnectionSpec ignored) {
                System.setProperty(KRB5_KEY, "/mutated/krb5.conf");
                System.setProperty(ZK_KEY, "zookeeper/mutated");
            }
            @Override public Connection connect(String jdbcUrl) { return null; }
            @Override public void reset() { resets.incrementAndGet(); }
        });

        assertThat(window.run(spec())).isNull();
        assertThat(System.getProperty(KRB5_KEY)).isEqualTo("/original/krb5.conf");
        assertThat(System.getProperty(ZK_KEY)).isEqualTo("zookeeper/original");
        assertThat(resets.get()).isEqualTo(1);
    }

    @Test void restoresGlobalStateAfterFailureAndWrapsInSqlException() throws Exception {
        System.setProperty(KRB5_KEY, "/original/krb5.conf");
        AtomicInteger resets = new AtomicInteger();
        KerberosLoginWindow window = new KerberosLoginWindow(new KerberosLoginWindow.KerberosLogin() {
            @Override public void login(KerberosConnectionSpec ignored) throws Exception {
                System.setProperty(KRB5_KEY, "/mutated/krb5.conf");
                throw new IllegalStateException("krb5 login blew up with /opt/keytab/hive531/hive.keytab");
            }
            @Override public Connection connect(String jdbcUrl) { throw new AssertionError("must not connect"); }
            @Override public void reset() { resets.incrementAndGet(); }
        });

        assertThatThrownBy(() -> window.run(spec()))
            .isInstanceOf(SQLException.class)
            .hasMessageNotContaining("/opt/keytab").hasMessageNotContaining("krb5 login blew up");
        assertThat(System.getProperty(KRB5_KEY)).isEqualTo("/original/krb5.conf");
        assertThat(resets.get()).isEqualTo(1);
    }

    @Test void clearsPropertiesThatWereAbsentBefore() throws Exception {
        KerberosLoginWindow window = new KerberosLoginWindow(new KerberosLoginWindow.KerberosLogin() {
            @Override public void login(KerberosConnectionSpec ignored) {
                System.setProperty(KRB5_KEY, "/mutated/krb5.conf");
            }
            @Override public Connection connect(String jdbcUrl) { return null; }
            @Override public void reset() { }
        });
        window.run(spec());
        assertThat(System.getProperty(KRB5_KEY)).isNull();
    }

    @Test void concurrentRunsDoNotInterleave() throws Exception {
        KerberosConnectionSpec spec = spec();
        AtomicInteger active = new AtomicInteger();
        AtomicInteger max = new AtomicInteger();
        AtomicInteger resets = new AtomicInteger();
        KerberosLoginWindow window = new KerberosLoginWindow(new KerberosLoginWindow.KerberosLogin() {
            @Override public void login(KerberosConnectionSpec ignored) throws InterruptedException {
                int now = active.incrementAndGet();
                max.accumulateAndGet(now, Math::max);
                Thread.sleep(20);
            }
            @Override public Connection connect(String jdbcUrl) { active.decrementAndGet(); return null; }
            @Override public void reset() { resets.incrementAndGet(); }
        });
        Thread first = new Thread(() -> runQuietly(window, spec));
        Thread second = new Thread(() -> runQuietly(window, spec));
        first.start();
        second.start();
        first.join();
        second.join();
        assertThat(max.get()).isEqualTo(1);
        assertThat(resets.get()).isEqualTo(2);
    }

    private static void runQuietly(KerberosLoginWindow window, KerberosConnectionSpec spec) {
        try { window.run(spec); }
        catch (SQLException ignored) { }
    }
}
