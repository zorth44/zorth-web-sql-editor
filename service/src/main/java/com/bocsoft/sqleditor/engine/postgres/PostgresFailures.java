package com.bocsoft.sqleditor.engine.postgres;

import com.bocsoft.sqleditor.engine.ConnectionFailure;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.sql.SQLException;
import java.util.Locale;
import javax.net.ssl.SSLException;

final class PostgresFailures {
    ConnectionFailure classify(Throwable failure) {
        Throwable current = failure; int depth = 0;
        while (current != null && depth++ < 12) {
            if (current instanceof SQLException) {
                SQLException sql = (SQLException) current; int sqlDepth = 0;
                while (sql != null && sqlDepth++ < 12) {
                    ConnectionFailure classified = classifySql(sql);
                    if (classified != null) return classified;
                    sql = sql.getNextException();
                }
            }
            String message = current.getMessage() == null ? "" : current.getMessage().toLowerCase(Locale.ROOT);
            if (message.contains("password authentication failed") || message.contains("auth failed")) {
                return new ConnectionFailure("AUTHENTICATION_FAILED", "身份验证失败");
            }
            if (message.contains("database") && message.contains("does not exist")) {
                return new ConnectionFailure("DATABASE_NOT_FOUND", "默认数据库不存在或不可访问");
            }
            current = current.getCause();
        }
        if (find(failure, SocketTimeoutException.class) != null) return new ConnectionFailure("CONNECTION_TIMEOUT", "连接超时，请检查主机、端口和网络策略");
        if (find(failure, ConnectException.class) != null) return new ConnectionFailure("CONNECTION_REFUSED", "连接被拒绝，请检查主机和端口");
        if (find(failure, SSLException.class) != null) return new ConnectionFailure("TLS_FAILED", "TLS 连接失败，请检查 SSL 配置");
        return new ConnectionFailure("CONNECTION_FAILED", "连接失败，请检查连接配置");
    }

    private ConnectionFailure classifySql(SQLException sql) {
        String state = sql.getSQLState();
        if ("28P01".equals(state) || "28000".equals(state)) return new ConnectionFailure("AUTHENTICATION_FAILED", "身份验证失败");
        if ("3D000".equals(state)) return new ConnectionFailure("DATABASE_NOT_FOUND", "默认数据库不存在或不可访问");
        String message = sql.getMessage() == null ? "" : sql.getMessage().toLowerCase(Locale.ROOT);
        if (message.contains("password authentication failed")) return new ConnectionFailure("AUTHENTICATION_FAILED", "身份验证失败");
        if (message.contains("database") && message.contains("does not exist")) {
            return new ConnectionFailure("DATABASE_NOT_FOUND", "默认数据库不存在或不可访问");
        }
        return null;
    }

    private <T extends Throwable> T find(Throwable failure, Class<T> type) {
        Throwable current = failure; int depth = 0;
        while (current != null && depth++ < 12) {
            if (type.isInstance(current)) return type.cast(current);
            current = current.getCause();
        }
        return null;
    }
}
