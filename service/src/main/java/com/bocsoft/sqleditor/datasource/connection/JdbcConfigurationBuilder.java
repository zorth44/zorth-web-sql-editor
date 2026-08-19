package com.bocsoft.sqleditor.datasource.connection;

import com.bocsoft.sqleditor.engine.EngineRegistry;
import org.springframework.stereotype.Component;

@Component
public class JdbcConfigurationBuilder {
    private final NetworkPolicy networkPolicy;
    private final EngineRegistry engines;
    public JdbcConfigurationBuilder(NetworkPolicy networkPolicy, EngineRegistry engines) {
        this.networkPolicy = networkPolicy;
        this.engines = engines;
    }

    public JdbcTarget build(ConnectionConfiguration configuration) {
        return engines.forConnection(configuration).buildJdbc(configuration, networkPolicy.resolve(configuration.getHost()));
    }
}
