package com.bocsoft.sqleditor.datasource.connection;

import com.bocsoft.sqleditor.engine.EngineRegistry;
import com.bocsoft.sqleditor.engine.EngineSupport;
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
        EngineSupport engine = engines.forConnection(configuration);
        ResolvedTarget resolved = engine.requiresHostResolution()
            ? networkPolicy.resolve(configuration.getHost()) : null;
        return engine.buildJdbc(configuration, resolved);
    }
}
