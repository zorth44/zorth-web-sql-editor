package com.bocsoft.sqleditor.engine.hive;

import com.bocsoft.sqleditor.common.ApiException;
import com.bocsoft.sqleditor.datasource.connection.ConnectionConfiguration;
import com.bocsoft.sqleditor.datasource.connection.JdbcTarget;
import com.bocsoft.sqleditor.datasource.connection.ResolvedTarget;
import com.bocsoft.sqleditor.engine.ConnectionFailure;
import com.bocsoft.sqleditor.engine.EngineField;
import java.io.UnsupportedEncodingException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;

final class HiveJdbc {
    static final String DRIVER_CLASS = "org.apache.hive.jdbc.HiveDriver";
    private static final String METASTORE_URIS = "hive.metastore.uris";
    private static final Pattern METASTORE_URI_PATTERN = Pattern.compile(
        "thrift://(?:\\[[0-9A-Fa-f:]+\\]|[A-Za-z0-9._-]+):\\d{1,5}"
            + "(?:,thrift://(?:\\[[0-9A-Fa-f:]+\\]|[A-Za-z0-9._-]+):\\d{1,5})*");

    static final List<EngineField> PROPERTY_FIELDS = Collections.unmodifiableList(Arrays.asList(
        EngineField.property(METASTORE_URIS, "TEXT", METASTORE_URIS, "", null)
    ));

    private static volatile boolean driverLoaded;

    Map<String, String> validateProperties(Map<String, String> input) {
        Map<String, String> safe = new LinkedHashMap<String, String>();
        if (input == null) return safe;
        for (Map.Entry<String, String> entry : input.entrySet()) {
            String key = entry.getKey();
            if (!METASTORE_URIS.equals(key)) throw invalid(key);
            String value = entry.getValue() == null ? "" : entry.getValue();
            if (!value.isEmpty() && !METASTORE_URI_PATTERN.matcher(value).matches()) throw invalid(key);
            safe.put(key, value);
        }
        return safe;
    }

    List<String> allowedPropertyKeys() {
        List<String> keys = new ArrayList<String>();
        for (EngineField field : PROPERTY_FIELDS) keys.add(field.getName());
        return keys;
    }

    JdbcTarget build(ConnectionConfiguration configuration, ResolvedTarget resolved) {
        Map<String, String> user = validateProperties(configuration.getProperties());
        ensureDriver();
        Properties properties = new Properties();
        for (Map.Entry<String, String> entry : user.entrySet()) properties.setProperty(entry.getKey(), entry.getValue());
        properties.setProperty("user", configuration.getUsername());
        properties.setProperty("password", configuration.getPassword());
        List<String> urls = new ArrayList<String>();
        for (InetAddress address : resolved.getAddresses()) {
            String host = address instanceof Inet6Address ? "[" + address.getHostAddress() + "]" : address.getHostAddress();
            urls.add("jdbc:hive2://" + host + ":" + configuration.getPort() + "/" + encode(configuration.getDefaultDatabase()));
        }
        return new JdbcTarget(urls, properties);
    }

    String jdbcUrlWithoutNamespace(String url) {
        int scheme = url.indexOf("://");
        int slash = scheme < 0 ? -1 : url.indexOf('/', scheme + 3);
        return slash < 0 ? url : url.substring(0, slash + 1);
    }

    ConnectionFailure missingDriverFailure() {
        return new ConnectionFailure("CONNECTION_FAILED", "未找到 Hive JDBC 驱动");
    }

    boolean missingOfficialDriver(Throwable failure) {
        Throwable current = failure;
        int depth = 0;
        while (current != null && depth++ < 12) {
            if (current instanceof ClassNotFoundException && mentionsDriver(current.getMessage())) return true;
            if (noSuitableDriver(current.getMessage())) return true;
            current = current.getCause();
        }
        return false;
    }

    static void ensureDriver() {
        if (driverLoaded) return;
        try {
            Class.forName(DRIVER_CLASS);
            driverLoaded = true;
        } catch (ClassNotFoundException ignored) {
            // Hive JDBC jar is dropped in at deploy time; URL assembly still uses jdbc:hive2://.
        }
    }

    private static boolean mentionsDriver(String message) {
        return message != null && message.contains(DRIVER_CLASS);
    }

    private static boolean noSuitableDriver(String message) {
        return message != null && message.toLowerCase(Locale.ROOT).contains("no suitable driver");
    }

    private ApiException invalid(String key) {
        return ApiException.validation("properties", "NOT_ALLOWED", "JDBC 参数 " + key + " 不在白名单中");
    }

    private String encode(String value) {
        if (value == null || value.isEmpty()) return "";
        try {
            return URLEncoder.encode(value, "UTF-8").replace("+", "%20");
        } catch (UnsupportedEncodingException exception) {
            throw new IllegalStateException("UTF-8 unavailable", exception);
        }
    }
}
