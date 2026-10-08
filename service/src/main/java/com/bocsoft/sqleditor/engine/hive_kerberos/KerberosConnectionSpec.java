package com.bocsoft.sqleditor.engine.hive_kerberos;

import com.bocsoft.sqleditor.config.SqlEditorProperties;
import com.bocsoft.sqleditor.datasource.connection.ConnectionConfiguration;
import com.bocsoft.sqleditor.datasource.connection.JdbcTarget;
import java.util.Collections;
import java.util.Properties;

public final class KerberosConnectionSpec {
    static final String ENVIRONMENT = "kerberos.environment";
    static final String KEYTAB_FILE = "kerberos.keytabFile";
    static final String QUEUE_NAME = "kerberos.queueName";
    static final String DATABASE = "kerberos.database";
    static final String TIMEOUT = "kerberos.timeoutSeconds";
    static final String KEYTAB_BASE_PATH = "kerberos.keytabBasePath";
    static final String KEYTAB_SUBDIRECTORY = "kerberos.keytabSubdirectory";
    static final String KRB5_CONF = "kerberos.krb5Conf";
    static final String REALM = "kerberos.realm";
    static final String ZK_QUORUM = "kerberos.zookeeperQuorum";
    static final String NAMESPACE = "kerberos.zookeeperNamespace";
    static final String PRINCIPAL_TEMPLATE = "kerberos.principalTemplate";

    private final String environment;
    private final String keytabFile;
    private final String queueName;
    private final String database;
    private final int timeoutSeconds;
    private final String keytabBasePath;
    private final String keytabSubdirectory;
    private final String krb5Conf;
    private final String realm;
    private final String zkQuorum;
    private final String namespace;
    private final String principalTemplate;

    KerberosConnectionSpec(String environment, String keytabFile, String queueName, String database, int timeoutSeconds,
                           String keytabBasePath, String keytabSubdirectory, String krb5Conf, String realm,
                           String zkQuorum, String namespace, String principalTemplate) {
        this.environment = environment;
        this.keytabFile = keytabFile;
        this.queueName = queueName;
        this.database = database;
        this.timeoutSeconds = timeoutSeconds;
        this.keytabBasePath = keytabBasePath;
        this.keytabSubdirectory = keytabSubdirectory;
        this.krb5Conf = krb5Conf;
        this.realm = realm;
        this.zkQuorum = zkQuorum;
        this.namespace = namespace;
        this.principalTemplate = principalTemplate;
    }

    public static KerberosConnectionSpec forConfiguration(ConnectionConfiguration configuration,
                                                          SqlEditorProperties.Kerberos kerberos,
                                                          String keytabSubdirectory) {
        String environment = configuration == null ? null : configuration.getEnvironment();
        String keytabFile = configuration == null ? null : configuration.getKeytabFile();
        String queueName = configuration == null ? null : configuration.getQueueName();
        String database = configuration == null ? null : configuration.getDefaultDatabase();
        int timeoutSeconds = configuration == null ? 10 : configuration.getTimeoutSeconds();
        SqlEditorProperties.Kerberos.Environment env = kerberos == null ? null : kerberos.environment(environment);
        if (env == null) {
            return new KerberosConnectionSpec(environment, keytabFile, queueName, database, timeoutSeconds,
                null, keytabSubdirectory, null, null, null, null, null);
        }
        return new KerberosConnectionSpec(environment, keytabFile, queueName, database, timeoutSeconds,
            kerberos.getKeytabBasePath(), keytabSubdirectory, env.getKrb5Conf(), env.getRealm(),
            env.getZookeeperQuorum(), env.getZookeeperNamespace(), env.getPrincipalTemplate());
    }

    public boolean isResolved() {
        return environment != null && keytabFile != null && keytabBasePath != null
            && krb5Conf != null && realm != null && zkQuorum != null;
    }

    public String getEnvironment() { return environment; }
    public String getKeytabFile() { return keytabFile; }
    public String getQueueName() { return queueName; }
    public String getDatabase() { return database; }
    public int getTimeoutSeconds() { return timeoutSeconds; }
    public String getKeytabBasePath() { return keytabBasePath; }
    public String getKeytabSubdirectory() { return keytabSubdirectory; }
    public String getKrb5Conf() { return krb5Conf; }
    public String getRealm() { return realm; }
    public String getZkQuorum() { return zkQuorum; }
    public String getNamespace() { return namespace; }
    public String getPrincipalTemplate() { return principalTemplate; }

    public String getKeytabPath() {
        return KerberosNames.keytabPath(keytabBasePath, keytabSubdirectory, keytabFile);
    }

    public String getLoginPrincipal() {
        return KerberosNames.loginPrincipal(keytabFile, realm);
    }

    public String getUrlPrincipal() {
        return KerberosNames.urlPrincipal(principalTemplate, realm);
    }

    public String getJdbcUrl() {
        return KerberosNames.serviceDiscoveryUrl(zkQuorum, database, namespace, getUrlPrincipal(), queueName);
    }

    public JdbcTarget toJdbcTarget() {
        Properties properties = new Properties();
        put(properties, ENVIRONMENT, environment);
        put(properties, KEYTAB_FILE, keytabFile);
        put(properties, QUEUE_NAME, queueName);
        put(properties, DATABASE, database);
        put(properties, TIMEOUT, String.valueOf(timeoutSeconds));
        put(properties, KEYTAB_BASE_PATH, keytabBasePath);
        put(properties, KEYTAB_SUBDIRECTORY, keytabSubdirectory);
        put(properties, KRB5_CONF, krb5Conf);
        put(properties, REALM, realm);
        put(properties, ZK_QUORUM, zkQuorum);
        put(properties, NAMESPACE, namespace);
        put(properties, PRINCIPAL_TEMPLATE, principalTemplate);
        String url = isResolved() ? getJdbcUrl() : "jdbc:hive2://";
        return new JdbcTarget(Collections.singletonList(url), properties);
    }

    public static KerberosConnectionSpec fromJdbcTarget(JdbcTarget target) {
        Properties properties = target.copyProperties();
        return new KerberosConnectionSpec(
            properties.getProperty(ENVIRONMENT),
            properties.getProperty(KEYTAB_FILE),
            properties.getProperty(QUEUE_NAME),
            properties.getProperty(DATABASE),
            parseTimeout(properties.getProperty(TIMEOUT)),
            properties.getProperty(KEYTAB_BASE_PATH),
            properties.getProperty(KEYTAB_SUBDIRECTORY),
            properties.getProperty(KRB5_CONF),
            properties.getProperty(REALM),
            properties.getProperty(ZK_QUORUM),
            properties.getProperty(NAMESPACE),
            properties.getProperty(PRINCIPAL_TEMPLATE));
    }

    private static int parseTimeout(String value) {
        if (value == null) return 10;
        try { return Integer.parseInt(value); }
        catch (NumberFormatException exception) { return 10; }
    }

    private static void put(Properties properties, String key, String value) {
        if (value != null) properties.setProperty(key, value);
    }
}
