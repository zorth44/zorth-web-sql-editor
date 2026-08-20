package com.bocsoft.sqleditor.engine.gbase8a;

import com.bocsoft.sqleditor.datasource.connection.JdbcTarget;
import com.bocsoft.sqleditor.engine.ConnectionFailure;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class Gbase8aJdbc {
    static final String DRIVER_CLASS = "com.gbase.jdbc.Driver";
    private static final String MYSQL_SCHEME = "jdbc:mysql://";
    private static final String GBASE_SCHEME = "jdbc:gbase://";
    private static volatile boolean driverLoaded;

    JdbcTarget rewrite(JdbcTarget mysql) {
        ensureDriver();
        List<String> urls = new ArrayList<String>();
        for (String url : mysql.getUrls()) urls.add(toGbaseUrl(url));
        return new JdbcTarget(urls, mysql.copyProperties());
    }

    String jdbcUrlWithoutNamespace(String url) {
        String gbase = toGbaseUrl(url);
        int slash = gbase.lastIndexOf('/');
        return slash < 0 ? gbase : gbase.substring(0, slash + 1);
    }

    ConnectionFailure missingDriverFailure() {
        return new ConnectionFailure("CONNECTION_FAILED", "未找到 GBase 8a 官方 JDBC 驱动");
    }

    boolean missingOfficialDriver(Throwable failure) {
        Throwable current = failure;
        int depth = 0;
        while (current != null && depth++ < 12) {
            if (current instanceof ClassNotFoundException && mentionsDriver(current.getMessage())) return true;
            if (current instanceof SQLException && noSuitableDriver(current.getMessage())) return true;
            if (noSuitableDriver(current.getMessage())) return true;
            current = current.getCause();
        }
        return false;
    }

    static String toGbaseUrl(String url) {
        if (url != null && url.startsWith(MYSQL_SCHEME)) {
            return GBASE_SCHEME + url.substring(MYSQL_SCHEME.length());
        }
        return url;
    }

    static void ensureDriver() {
        if (driverLoaded) return;
        try {
            Class.forName(DRIVER_CLASS);
            driverLoaded = true;
        } catch (ClassNotFoundException ignored) {
            // Official jar is dropped in at deploy time; URL assembly still uses jdbc:gbase://.
        }
    }

    private static boolean mentionsDriver(String message) {
        return message != null && message.contains(DRIVER_CLASS);
    }

    private static boolean noSuitableDriver(String message) {
        return message != null && message.toLowerCase(Locale.ROOT).contains("no suitable driver");
    }
}
