package com.bocsoft.sqleditor.engine.mysql;

import com.bocsoft.sqleditor.common.ApiException;
import com.bocsoft.sqleditor.datasource.connection.ConnectionConfiguration;
import com.bocsoft.sqleditor.datasource.connection.JdbcTarget;
import com.bocsoft.sqleditor.datasource.connection.ResolvedTarget;
import java.io.UnsupportedEncodingException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URLEncoder;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

final class MysqlJdbc {
    Map<String, String> validateProperties(Map<String, String> input) {
        Map<String, String> safe = new LinkedHashMap<String, String>();
        if (input == null) return safe;
        for (Map.Entry<String, String> entry : input.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if ("serverTimezone".equals(key)) validateTimeZone(value);
            else if ("characterSetResults".equals(key)) oneOf(key, value, "utf8", "UTF-8");
            else if ("zeroDateTimeBehavior".equals(key)) oneOf(key, value, "CONVERT_TO_NULL", "EXCEPTION", "ROUND");
            else if ("tinyInt1isBit".equals(key) || "sendFractionalSeconds".equals(key)) oneOf(key, value, "true", "false");
            else throw invalid(key);
            safe.put(key, value);
        }
        return safe;
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
