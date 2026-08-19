package com.bocsoft.sqleditor.engine.mysql;

import com.bocsoft.sqleditor.engine.ConnectionFailure;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.sql.SQLException;
import javax.net.ssl.SSLException;

final class MysqlFailures {
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
            String message = current.getMessage() == null ? "" : current.getMessage().toLowerCase(java.util.Locale.ROOT);
            if (message.contains("access denied for user")) return new ConnectionFailure("AUTHENTICATION_FAILED", "身份验证失败");
            if (message.contains("unknown database")) return new ConnectionFailure("DATABASE_NOT_FOUND", "默认数据库不存在或不可访问");
            current = current.getCause();
        }
        if (find(failure, SocketTimeoutException.class) != null) return new ConnectionFailure("CONNECTION_TIMEOUT", "连接超时，请检查主机、端口和网络策略");
        if (find(failure, ConnectException.class) != null) return new ConnectionFailure("CONNECTION_REFUSED", "连接被拒绝，请检查主机和端口");
        if (find(failure, SSLException.class) != null) return new ConnectionFailure("TLS_FAILED", "TLS 连接失败，请检查 SSL 配置");
        SQLException outer = find(failure, SQLException.class);
        if (outer != null && "08001".equals(outer.getSQLState()) && hasCauseClass(failure, "com.mysql.cj.exceptions.UnableToConnectException")) {
            return new ConnectionFailure("AUTHENTICATION_FAILED", "身份验证失败");
        }
        return new ConnectionFailure("CONNECTION_FAILED", "连接失败，请检查连接配置");
    }

    private ConnectionFailure classifySql(SQLException sql) {
        if (sql.getErrorCode() == 1045 || "28000".equals(sql.getSQLState())) return new ConnectionFailure("AUTHENTICATION_FAILED", "身份验证失败");
        if (sql.getErrorCode() == 1049 || ("42000".equals(sql.getSQLState()) && sql.getMessage() != null && sql.getMessage().toLowerCase(java.util.Locale.ROOT).contains("unknown database"))) {
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
    private boolean hasCauseClass(Throwable failure, String className) {
        Throwable current = failure; int depth = 0;
        while (current != null && depth++ < 12) {
            if (className.equals(current.getClass().getName())) return true;
            current = current.getCause();
        }
        return false;
    }
}
