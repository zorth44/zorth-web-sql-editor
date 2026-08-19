package com.bocsoft.sqleditor.engine;

import com.bocsoft.sqleditor.common.ApiException;
import com.bocsoft.sqleditor.datasource.connection.ConnectionConfiguration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class EngineRegistry {
    private final Map<String, EngineSupport> engines;

    public EngineRegistry(List<EngineSupport> engines) {
        Map<String, EngineSupport> map = new LinkedHashMap<String, EngineSupport>();
        if (engines != null) {
            for (EngineSupport engine : engines) map.put(engine.id(), engine);
        }
        this.engines = Collections.unmodifiableMap(map);
    }

    public boolean registered(String id) {
        return id != null && engines.containsKey(id);
    }

    public EngineSupport require(String id) {
        EngineSupport engine = id == null ? null : engines.get(id);
        if (engine == null) throw ApiException.validation("engine", "INVALID", "不支持的数据库类型");
        return engine;
    }

    public EngineSupport requireSaved(String id) {
        EngineSupport engine = id == null ? null : engines.get(id);
        if (engine == null) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "ENGINE_NOT_SUPPORTED", "数据源类型不受支持");
        }
        return engine;
    }

    public EngineSupport forConnection(ConnectionConfiguration configuration) {
        String id = configuration.getEngine();
        if (!StringUtils.hasText(id)) return require(EngineId.MYSQL);
        return requireSaved(id);
    }
}
