package com.bocsoft.sqleditor.datasource.connection;

import com.bocsoft.sqleditor.datasource.api.ConnectionTestResult;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

@Component
public class ShortLivedConnectionTester {
    private final JdbcConfigurationBuilder builder;
    private final ConnectionFailureClassifier classifier;
    public ShortLivedConnectionTester(JdbcConfigurationBuilder builder, ConnectionFailureClassifier classifier) {
        this.builder=builder; this.classifier=classifier;
    }

    public ConnectionTestResult test(ConnectionConfiguration configuration) {
        long started=System.nanoTime();
        long deadline=started + TimeUnit.SECONDS.toNanos(configuration.getTimeoutSeconds());
        JdbcTarget target=builder.build(configuration);
        Throwable last=null;
        for (String url : target.getUrls()) {
            long remaining=Math.max(1, TimeUnit.NANOSECONDS.toMillis(deadline-System.nanoTime()));
            if (System.nanoTime() >= deadline) break;
            Properties properties=target.copyProperties();
            properties.setProperty("connectTimeout", String.valueOf(Math.min(remaining, 30000)));
            try (Connection connection=DriverManager.getConnection(withoutDatabase(url), properties)) {
                if(configuration.getDefaultDatabase()!=null && !configuration.getDefaultDatabase().isEmpty()){
                    try{connection.setCatalog(configuration.getDefaultDatabase());}
                    catch(Throwable databaseFailure){last=new java.sql.SQLException("Default database unavailable","42000",1049,databaseFailure);continue;}
                }
                DatabaseMetaData metadata=connection.getMetaData();
                return ConnectionTestResult.success(metadata.getDatabaseProductVersion(), elapsed(started));
            } catch (Throwable failure) { last=failure; }
        }
        ConnectionFailureClassifier.Failure safe = classifier.classify(last == null
            ? new SocketTimeoutExceptionWithoutDetails() : last);
        return ConnectionTestResult.failure(elapsed(started), safe.getMessage(), safe.getCode());
    }

    private long elapsed(long started) { return TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started); }
    private String withoutDatabase(String url){int slash=url.lastIndexOf('/');return slash<0?url:url.substring(0,slash+1);}
    private static final class SocketTimeoutExceptionWithoutDetails extends java.net.SocketTimeoutException { private static final long serialVersionUID=1L; }
}
