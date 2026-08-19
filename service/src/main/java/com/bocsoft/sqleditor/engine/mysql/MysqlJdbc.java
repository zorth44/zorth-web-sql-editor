package com.bocsoft.sqleditor.engine.mysql;

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
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

final class MysqlJdbc {
    static final List<EngineField> PROPERTY_FIELDS = Collections.unmodifiableList(Arrays.asList(
        EngineField.property("serverTimezone", "TEXT", "serverTimezone", "Asia/Shanghai", null),
        EngineField.property("characterSetResults", "SELECT", "characterSetResults", null, Arrays.asList("utf8", "UTF-8")),
        EngineField.property("zeroDateTimeBehavior", "SELECT", "zeroDateTimeBehavior", null, Arrays.asList("EXCEPTION", "CONVERT_TO_NULL", "ROUND")),
        EngineField.property("tinyInt1isBit", "SELECT", "tinyInt1isBit", null, Arrays.asList("true", "false")),
        EngineField.property("sendFractionalSeconds", "SELECT", "sendFractionalSeconds", null, Arrays.asList("true", "false"))
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
            if ("serverTimezone".equals(key)) validateTimeZone(value);
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
        properties.setProperty("allowMultiQueries", "false");
        properties.setProperty("allowLoadLocalInfile", "false");
        properties.setProperty("allowUrlInLocalInfile", "false");
        properties.setProperty("autoDeserialize", "false");
        properties.setProperty("useUnicode", "true");
        properties.setProperty("characterEncoding", "UTF-8");
        properties.setProperty("user", configuration.getUsername());
        properties.setProperty("password", configuration.getPassword());
        List<String> urls = new ArrayList<String>();
        for (InetAddress address : resolved.getAddresses()) {
            String host = address instanceof Inet6Address ? "[" + address.getHostAddress() + "]" : address.getHostAddress();
            urls.add("jdbc:mysql://" + host + ":" + configuration.getPort() + "/" + encode(configuration.getDefaultDatabase()));
        }
        return new JdbcTarget(urls, properties);
    }

    String jdbcUrlWithoutNamespace(String url) {
        int slash = url.lastIndexOf('/');
        return slash < 0 ? url : url.substring(0, slash + 1);
    }

    private void applySsl(Properties properties, String mode) {
        if ("DISABLED".equals(mode)) {
            properties.setProperty("useSSL", "false");
        } else if ("PREFERRED".equals(mode)) {
            properties.setProperty("useSSL", "true"); properties.setProperty("requireSSL", "false");
            properties.setProperty("verifyServerCertificate", "false");
        } else if ("REQUIRED".equals(mode)) {
            properties.setProperty("useSSL", "true"); properties.setProperty("requireSSL", "true");
            properties.setProperty("verifyServerCertificate", "false");
        } else throw ApiException.validation("sslMode", "INVALID", "SSL 模式不合法");
    }

    private void validateTimeZone(String value) {
        try {
            if (value == null || value.length() > 64) throw new DateTimeException("invalid");
            ZoneId.of(value);
        } catch (DateTimeException exception) { throw invalid("serverTimezone"); }
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
