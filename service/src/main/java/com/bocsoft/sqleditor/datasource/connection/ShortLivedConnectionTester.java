package com.bocsoft.sqleditor.datasource.connection;

import com.bocsoft.sqleditor.datasource.api.ConnectionTestResult;
import com.bocsoft.sqleditor.engine.ConnectionFailure;
import com.bocsoft.sqleditor.engine.EngineRegistry;
import com.bocsoft.sqleditor.engine.EngineSupport;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

@Component
public class ShortLivedConnectionTester {
    private final JdbcConfigurationBuilder builder;
    private final EngineRegistry engines;
    public ShortLivedConnectionTester(JdbcConfigurationBuilder builder, EngineRegistry engines) {
        this.builder=builder; this.engines=engines;
    }

    public ConnectionTestResult test(ConnectionConfiguration configuration) {
        long started=System.nanoTime();
        long deadline=started + TimeUnit.SECONDS.toNanos(configuration.getTimeoutSeconds());
        EngineSupport engine=engines.forConnection(configuration);
        JdbcTarget target=builder.build(configuration);
        Throwable last=null;
        for (String url : target.getUrls()) {
            long remaining=Math.max(1, TimeUnit.NANOSECONDS.toMillis(deadline-System.nanoTime()));
            if (System.nanoTime() >= deadline) break;
            Properties properties=target.copyProperties();
            properties.setProperty("connectTimeout", String.valueOf(Math.min(remaining, 30000)));
            try (Connection connection=DriverManager.getConnection(engine.jdbcUrlWithoutNamespace(url), properties)) {
                engine.verifyDefaultNamespace(connection, configuration.getDefaultDatabase());
                DatabaseMetaData metadata=connection.getMetaData();
                return ConnectionTestResult.success(metadata.getDatabaseProductVersion(), elapsed(started));
            } catch (Throwable failure) { last=failure; }
        }
        ConnectionFailure safe = engine.classifyConnectionFailure(last == null
            ? new SocketTimeoutExceptionWithoutDetails() : last);
        return ConnectionTestResult.failure(elapsed(started), safe.getMessage(), safe.getCode());
    }

    private long elapsed(long started) { return TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started); }
    private static final class SocketTimeoutExceptionWithoutDetails extends java.net.SocketTimeoutException { private static final long serialVersionUID=1L; }
}
