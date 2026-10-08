package com.bocsoft.sqleditor.engine.hive_kerberos;

import com.bocsoft.sqleditor.common.ApiException;
import com.bocsoft.sqleditor.datasource.connection.NetworkPolicy;

public final class KerberosEndpoints {
    private KerberosEndpoints() { }

    public static void assertAllowed(NetworkPolicy policy, String zkQuorum) {
        if (zkQuorum == null || zkQuorum.trim().isEmpty()) {
            throw ApiException.validation("environment", "INVALID", "Kerberos ZooKeeper 环境未配置");
        }
        for (String endpoint : zkQuorum.split(",")) {
            String host = hostOf(endpoint.trim());
            if (host.isEmpty()) {
                throw ApiException.validation("environment", "INVALID", "Kerberos ZooKeeper 端点格式不合法");
            }
            policy.resolve(host);
        }
    }

    static String hostOf(String endpoint) {
        if (endpoint.isEmpty()) return endpoint;
        if (endpoint.charAt(0) == '[') {
            int close = endpoint.indexOf(']');
            return close < 0 ? endpoint : endpoint.substring(1, close);
        }
        int firstColon = endpoint.indexOf(':');
        int lastColon = endpoint.lastIndexOf(':');
        if (firstColon >= 0 && firstColon == lastColon) return endpoint.substring(0, firstColon);
        return endpoint;
    }
}
