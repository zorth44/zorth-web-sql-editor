package com.bocsoft.sqleditor.datasource.connection;

import java.util.Collections;
import java.util.List;
import java.util.Properties;

public final class JdbcTarget {
    private final List<String> urls;
    private final Properties properties;
    JdbcTarget(List<String> urls, Properties properties) {
        this.urls=Collections.unmodifiableList(urls); this.properties=properties;
    }
    public List<String> getUrls() { return urls; }
    public Properties copyProperties() { Properties copy=new Properties(); copy.putAll(properties); return copy; }
}
