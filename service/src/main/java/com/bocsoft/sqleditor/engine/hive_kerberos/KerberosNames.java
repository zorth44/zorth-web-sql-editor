package com.bocsoft.sqleditor.engine.hive_kerberos;

import java.util.Locale;

public final class KerberosNames {
    private static final String DEFAULT_PRINCIPAL_TEMPLATE = "hadoop/_HOST@{realm}";
    private static final String DEFAULT_NAMESPACE = "hiveserver2";

    private KerberosNames() { }

    public static String keytabPath(String basePath, String subdirectory, String fileName) {
        if (basePath == null || fileName == null) return null;
        StringBuilder path = new StringBuilder(basePath);
        if (subdirectory != null && !subdirectory.isEmpty()) {
            appendSeparator(path);
            path.append(subdirectory);
        }
        appendSeparator(path);
        return path.append(fileName).toString();
    }

    public static String loginPrincipal(String keytabFileName, String realm) {
        if (keytabFileName == null || realm == null) return null;
        String name = baseName(keytabFileName);
        if (name.toLowerCase(Locale.ROOT).endsWith(".keytab")) {
            name = name.substring(0, name.length() - ".keytab".length());
        }
        return name + "@" + realm;
    }

    public static String urlPrincipal(String template, String realm) {
        if (realm == null) return null;
        String resolved = template == null || template.trim().isEmpty()
            ? DEFAULT_PRINCIPAL_TEMPLATE : template.trim();
        return resolved.replace("{realm}", realm);
    }

    public static String zookeeperPrincipal(String realm) {
        String host = realm == null || realm.trim().isEmpty() ? "hadoop" : "hadoop." + realm.toLowerCase(Locale.ROOT);
        return "zookeeper/" + host;
    }

    public static String serviceDiscoveryUrl(String zkQuorum, String database, String namespace,
                                             String principal, String queueName) {
        if (zkQuorum == null || zkQuorum.trim().isEmpty() || principal == null) return null;
        String databaseName = database == null || database.isEmpty() ? "default" : database;
        String resolvedNamespace = namespace == null || namespace.isEmpty() ? DEFAULT_NAMESPACE : namespace;
        StringBuilder url = new StringBuilder("jdbc:hive2://").append(zkQuorum).append('/').append(databaseName)
            .append(";serviceDiscoveryMode=zooKeeper")
            .append(";zooKeeperNamespace=").append(resolvedNamespace)
            .append(";principal=").append(principal);
        if (queueName != null && !queueName.isEmpty()) {
            url.append("?tez.queue.name=").append(queueName);
        }
        return url.toString();
    }

    private static void appendSeparator(StringBuilder path) {
        char last = path.charAt(path.length() - 1);
        if (last != '/' && last != '\\') path.append('/');
    }

    private static String baseName(String fileName) {
        int slash = Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\'));
        return slash < 0 ? fileName : fileName.substring(slash + 1);
    }
}
