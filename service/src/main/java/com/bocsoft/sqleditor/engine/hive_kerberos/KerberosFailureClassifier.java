package com.bocsoft.sqleditor.engine.hive_kerberos;

import com.bocsoft.sqleditor.engine.ConnectionFailure;
import java.util.Locale;

public final class KerberosFailureClassifier {
    private static final String[] AUTH_MARKERS = {
        "krb", "kerberos", "gss", "sasl", "principal", "keytab",
        "authentication", "login failure", "no valid credentials"
    };

    public ConnectionFailure classify(Throwable failure) {
        if (mentionsAuthentication(failure)) {
            return new ConnectionFailure("AUTHENTICATION_FAILED", "Kerberos 身份验证失败");
        }
        return new ConnectionFailure("CONNECTION_FAILED", "Kerberos 连接失败，请检查环境与凭据配置");
    }

    private boolean mentionsAuthentication(Throwable failure) {
        Throwable current = failure;
        int depth = 0;
        while (current != null && depth++ < 12) {
            if (current.getMessage() != null) {
                String message = current.getMessage().toLowerCase(Locale.ROOT);
                for (String marker : AUTH_MARKERS) {
                    if (message.contains(marker)) return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }
}
