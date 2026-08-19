package com.bocsoft.sqleditor.datasource.connection;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ConnectionConfiguration {
    private final String engine;
    private final String host; private final int port; private final String username; private final String password;
    private final String defaultDatabase; private final String sslMode; private final int timeoutSeconds;
    private final Map<String, String> properties;
    public ConnectionConfiguration(String host, int port, String username, String password,
                                   String defaultDatabase, String sslMode, int timeoutSeconds,
                                   Map<String, String> properties) {
        this(com.bocsoft.sqleditor.engine.EngineId.MYSQL, host, port, username, password, defaultDatabase, sslMode, timeoutSeconds, properties);
    }
    public ConnectionConfiguration(String engine, String host, int port, String username, String password,
                                   String defaultDatabase, String sslMode, int timeoutSeconds,
                                   Map<String, String> properties) {
        this.engine=engine; this.host=host; this.port=port; this.username=username; this.password=password;
        this.defaultDatabase=defaultDatabase; this.sslMode=sslMode; this.timeoutSeconds=timeoutSeconds;
        this.properties=Collections.unmodifiableMap(new LinkedHashMap<String, String>(properties));
    }
    public String getEngine() { return engine; }
    public String getHost() { return host; } public int getPort() { return port; }
    public String getUsername() { return username; } public String getPassword() { return password; }
    public String getDefaultDatabase() { return defaultDatabase; } public String getSslMode() { return sslMode; }
    public int getTimeoutSeconds() { return timeoutSeconds; } public Map<String,String> getProperties() { return properties; }
}
