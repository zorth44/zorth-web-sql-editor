package com.bocsoft.sqleditor.datasource.connection;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.sql.SQLException;
import javax.net.ssl.SSLException;
import org.springframework.stereotype.Component;

@Component
public class ConnectionFailureClassifier {
    public Failure classify(Throwable failure) {
        Throwable current=failure;int depth=0;
        while(current!=null && depth++<12){
            if(current instanceof SQLException){SQLException sql=(SQLException)current;int sqlDepth=0;while(sql!=null && sqlDepth++<12){Failure classified=classifySql(sql);if(classified!=null)return classified;sql=sql.getNextException();}}
            String message=current.getMessage()==null?"":current.getMessage().toLowerCase(java.util.Locale.ROOT);
            if(message.contains("access denied for user"))return new Failure("AUTHENTICATION_FAILED","身份验证失败");
            if(message.contains("unknown database"))return new Failure("DATABASE_NOT_FOUND","默认数据库不存在或不可访问");
            current=current.getCause();
        }
        if (find(failure, SocketTimeoutException.class) != null) return new Failure("CONNECTION_TIMEOUT", "连接超时，请检查主机、端口和网络策略");
        if (find(failure, ConnectException.class) != null) return new Failure("CONNECTION_REFUSED", "连接被拒绝，请检查主机和端口");
        if (find(failure, SSLException.class) != null) return new Failure("TLS_FAILED", "TLS 连接失败，请检查 SSL 配置");
        SQLException outer=find(failure,SQLException.class);
        if(outer!=null && "08001".equals(outer.getSQLState()) && hasCauseClass(failure,"com.mysql.cj.exceptions.UnableToConnectException"))return new Failure("AUTHENTICATION_FAILED","身份验证失败");
        return new Failure("CONNECTION_FAILED", "连接失败，请检查连接配置");
    }

    private Failure classifySql(SQLException sql){if(sql.getErrorCode()==1045 || "28000".equals(sql.getSQLState()))return new Failure("AUTHENTICATION_FAILED","身份验证失败");if(sql.getErrorCode()==1049 || ("42000".equals(sql.getSQLState()) && sql.getMessage()!=null && sql.getMessage().toLowerCase(java.util.Locale.ROOT).contains("unknown database")))return new Failure("DATABASE_NOT_FOUND","默认数据库不存在或不可访问");return null;}

    private <T extends Throwable> T find(Throwable failure, Class<T> type) {
        Throwable current=failure; int depth=0;
        while (current != null && depth++ < 12) {
            if (type.isInstance(current)) return type.cast(current);
            current=current.getCause();
        }
        return null;
    }
    private boolean hasCauseClass(Throwable failure,String className){Throwable current=failure;int depth=0;while(current!=null&&depth++<12){if(className.equals(current.getClass().getName()))return true;current=current.getCause();}return false;}
    public static final class Failure {
        private final String code; private final String message;
        Failure(String code, String message) { this.code=code; this.message=message; }
        public String getCode() { return code; } public String getMessage() { return message; }
    }
}
