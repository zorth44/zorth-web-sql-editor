package com.bocsoft.sqleditor.datasource.connection;

import com.bocsoft.sqleditor.common.ApiException;
import com.bocsoft.sqleditor.config.SqlEditorProperties;
import com.bocsoft.sqleditor.datasource.PoolLifecycle;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import javax.annotation.PreDestroy;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DynamicPoolManager implements PoolLifecycle {
    private final Map<String, PoolEntry> pools = new ConcurrentHashMap<String, PoolEntry>();
    private final JdbcConfigurationBuilder builder;
    private final SqlEditorProperties.Pools limits;
    private final MeterRegistry registry;

    public DynamicPoolManager(JdbcConfigurationBuilder builder, SqlEditorProperties properties, MeterRegistry registry) {
        this.builder=builder; this.limits=properties.getPools(); this.registry=registry; registry.gauge("sql_editor_target_pool_count",pools,Map::size);
    }

    public Connection borrow(String id,long version,ConnectionConfiguration configuration) throws SQLException {
        long started=System.nanoTime();
        try {
        PoolEntry entry=pools.get(id);
        if(entry==null || entry.version!=version){
            synchronized(this){
                entry=pools.get(id);
                if(entry==null || entry.version!=version){
                    if(entry!=null){pools.remove(id);entry.close();}
                    if(pools.size()>=limits.getMaxPools()) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"TARGET_POOL_LIMIT_EXCEEDED","目标连接池数量已达上限");
                    entry=create(id,version,configuration); pools.put(id,entry);
                }
            }
        }
        entry.touch(); Connection connection=entry.dataSource.getConnection();recordWait(started,"success");return connection;
        } catch (SQLException | RuntimeException exception) { recordWait(started,"failed");throw exception; }
    }

    @Override public void invalidate(String id){PoolEntry entry=pools.remove(id);if(entry!=null)entry.close();}
    public int size(){return pools.size();}

    @Scheduled(fixedDelay=60000)
    public void retireIdle(){long idle=TimeUnit.MINUTES.toNanos(limits.getIdlePoolMinutes());long now=System.nanoTime();for(Map.Entry<String,PoolEntry> item:pools.entrySet()){if(now-item.getValue().lastAccessNanos>idle && pools.remove(item.getKey(),item.getValue()))item.getValue().close();}}
    @PreDestroy public void close(){for(PoolEntry entry:pools.values())entry.close();pools.clear();}

    private PoolEntry create(String id,long version,ConnectionConfiguration configuration){
        JdbcTarget target=builder.build(configuration); Properties properties=target.copyProperties();
        HikariConfig config=new HikariConfig(); config.setPoolName("sql-ds-"+id.substring(0,Math.min(8,id.length())));
        config.setJdbcUrl(target.getUrls().get(0)); config.setDataSourceProperties(properties);
        config.setMaximumPoolSize(limits.getMaxPoolSize()); config.setMinimumIdle(0);
        config.setConnectionTimeout(TimeUnit.SECONDS.toMillis(configuration.getTimeoutSeconds()));
        config.setIdleTimeout(TimeUnit.MINUTES.toMillis(10)); config.setAutoCommit(true);
        return new PoolEntry(version,new HikariDataSource(config));
    }

    private void recordWait(long started,String outcome){Timer.builder("sql_editor_target_pool_borrow_wait").tag("outcome",outcome).register(registry).record(Duration.ofNanos(Math.max(0,System.nanoTime()-started)));}

    private static final class PoolEntry{
        private final long version;private final HikariDataSource dataSource;private volatile long lastAccessNanos;
        private PoolEntry(long version,HikariDataSource dataSource){this.version=version;this.dataSource=dataSource;touch();}
        private void touch(){lastAccessNanos=System.nanoTime();}private void close(){dataSource.close();}
    }
}
