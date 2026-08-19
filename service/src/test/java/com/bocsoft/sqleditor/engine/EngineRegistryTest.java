package com.bocsoft.sqleditor.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bocsoft.sqleditor.common.ApiException;
import com.bocsoft.sqleditor.datasource.connection.ConnectionConfiguration;
import com.bocsoft.sqleditor.engine.mysql.MysqlEngineSupport;
import java.util.Collections;
import org.junit.jupiter.api.Test;

class EngineRegistryTest {
    private final EngineRegistry registry = new EngineRegistry(Collections.<EngineSupport>singletonList(new MysqlEngineSupport()));

    @Test void requireRejectsUnregisteredEngine() {
        assertThatThrownBy(() -> registry.require("POSTGRESQL")).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.getCode()).isEqualTo("VALIDATION_FAILED");
        });
    }

    @Test void requireSavedFailsClosedForUnknownPersistedEngine() {
        assertThatThrownBy(() -> registry.requireSaved("ORACLE")).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.getCode()).isEqualTo("ENGINE_NOT_SUPPORTED");
        });
        assertThatThrownBy(() -> registry.requireSaved(null)).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.getCode()).isEqualTo("ENGINE_NOT_SUPPORTED");
        });
    }

    @Test void missingEngineOnUnsavedConfigurationDefaultsToMysql() {
        ConnectionConfiguration configuration = new ConnectionConfiguration(null, "h", 3306, "u", "p", null, "DISABLED", 10, Collections.emptyMap());
        assertThat(registry.forConnection(configuration).id()).isEqualTo(EngineId.MYSQL);
    }
}
