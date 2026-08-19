package com.bocsoft.sqleditor.engine.postgres;

import com.bocsoft.sqleditor.common.ApiException;
import com.bocsoft.sqleditor.datasource.connection.ConnectionConfiguration;
import com.bocsoft.sqleditor.datasource.connection.JdbcTarget;
import com.bocsoft.sqleditor.datasource.connection.ResolvedTarget;
import com.bocsoft.sqleditor.engine.EngineField;
import com.bocsoft.sqleditor.engine.EngineFieldOption;
import java.io.UnsupportedEncodingException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

final class PostgresJdbc {
    static final List<EngineField> PROPERTY_FIELDS = Collections.unmodifiableList(Arrays.asList(
        EngineField.property("ApplicationName", "TEXT", "ApplicationName", "zorth-sql-editor", null),
        EngineField.property("stringtype", "SELECT", "stringtype", null, Arrays.asList("unspecified", "varchar")),
        EngineField.property("tcpKeepAlive", "SELECT", "tcpKeepAlive", null, Arrays.asList("true", "false")),
        EngineField.property("reWriteBatchedInserts", "SELECT", "reWriteBatchedInserts", null, Arrays.asList("true", "false"))
    ));

    Map<String, String> validateProperties(Map<String, String> input) {
        Map<String, String> safe = new LinkedHashMap<String, String>();
        if (input == null) return safe;
        Map<String, EngineField> fields = propertyFieldsByName();
        for (Map.Entry<String, String> entry : input.entrySet()) {
            String key = entry.getKey();
            EngineField field = fields.get(key);
            if (field == null) throw invalid(key);
            String value = entry.getValue();
            if ("ApplicationName".equals(key)) validateApplicationName(value);
            else oneOf(key, value, optionValues(field));
            safe.put(key, value);
        }
        return safe;
    }

    List<String> allowedPropertyKeys() {
        List<String> keys = new ArrayList<String>();
        for (EngineField field : PROPERTY_FIELDS) keys.add(field.getName());
        return keys;
    }

    private Map<String, EngineField> propertyFieldsByName() {
        Map<String, EngineField> fields = new LinkedHashMap<String, EngineField>();
        for (EngineField field : PROPERTY_FIELDS) fields.put(field.getName(), field);
        return fields;
    }

    private String[] optionValues(EngineField field) {
        List<EngineFieldOption> options = field.getOptions();
        if (options == null) return new String[0];
        String[] values = new String[options.size()];
        for (int i = 0; i < options.size(); i++) values[i] = options.get(i).getValue();
        return values;
    }

    JdbcTarget build(ConnectionConfiguration configuration, ResolvedTarget resolved) {
        Map<String, String> user = validateProperties(configuration.getProperties());
        Properties properties = new Properties();
        for (Map.Entry<String, String> entry : user.entrySet()) properties.setProperty(entry.getKey(), entry.getValue());
        applySsl(properties, configuration.getSslMode());
        properties.setProperty("user", configuration.getUsername());
        properties.setProperty("password", configuration.getPassword());
        int timeout = Math.max(1, configuration.getTimeoutSeconds());
        properties.setProperty("connectTimeout", String.valueOf(timeout));
        properties.setProperty("loginTimeout", String.valueOf(timeout));
        List<String> urls = new ArrayList<String>();
        for (InetAddress address : resolved.getAddresses()) {
            String host = address instanceof Inet6Address ? "[" + address.getHostAddress() + "]" : address.getHostAddress();
            urls.add("jdbc:postgresql://" + host + ":" + configuration.getPort() + "/" + encode(configuration.getDefaultDatabase()));
        }
        return new JdbcTarget(urls, properties);
    }

    String jdbcUrlWithoutNamespace(String url) {
        return url;
    }

    private void applySsl(Properties properties, String mode) {
        if ("DISABLED".equals(mode)) {
            properties.setProperty("ssl", "false");
            properties.setProperty("sslmode", "disable");
        } else if ("PREFERRED".equals(mode)) {
            properties.setProperty("ssl", "true");
            properties.setProperty("sslmode", "prefer");
        } else if ("REQUIRED".equals(mode)) {
            properties.setProperty("ssl", "true");
            properties.setProperty("sslmode", "require");
        } else throw ApiException.validation("sslMode", "INVALID", "SSL 模式不合法");
    }

    private void validateApplicationName(String value) {
        if (value == null || value.isEmpty() || value.length() > 64 || value.indexOf('\0') >= 0) throw invalid("ApplicationName");
    }
    private void oneOf(String key, String value, String... allowed) {
        if (!Arrays.asList(allowed).contains(value)) throw invalid(key);
    }
    private ApiException invalid(String key) {
        return ApiException.validation("properties", "NOT_ALLOWED", "JDBC 参数 " + key + " 不在白名单中");
    }
    private String encode(String value) {
        if (value == null || value.isEmpty()) return "";
        try { return URLEncoder.encode(value, "UTF-8").replace("+", "%20"); }
        catch (UnsupportedEncodingException exception) { throw new IllegalStateException("UTF-8 unavailable", exception); }
    }
}
