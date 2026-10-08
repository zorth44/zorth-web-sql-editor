package com.bocsoft.sqleditor.engine.hive_kerberos;

import com.bocsoft.sqleditor.datasource.connection.NetworkPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KerberosConnectorFactory {
    private static final String VENDOR_LOGIN = "com.bocsoft.sqleditor.engine.hive_kerberos.vendor.TbdsKerberosHiveConnector";

    @Bean
    public KerberosHiveConnector kerberosHiveConnector(NetworkPolicy networkPolicy) {
        KerberosLoginWindow.KerberosLogin login = resolveVendorLogin();
        if (login == null) return new DisabledKerberosHiveConnector();
        return new KerberosHiveConnectorImpl(networkPolicy, new KerberosLoginWindow(login));
    }

    private KerberosLoginWindow.KerberosLogin resolveVendorLogin() {
        try {
            Class<?> type = Class.forName(VENDOR_LOGIN);
            return (KerberosLoginWindow.KerberosLogin) type.getDeclaredConstructor().newInstance();
        } catch (ClassNotFoundException absent) {
            return null;
        } catch (LinkageError absent) {
            return null;
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Kerberos connector initialization failed", failure);
        }
    }
}
