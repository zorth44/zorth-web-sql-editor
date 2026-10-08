package com.bocsoft.sqleditor.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bocsoft.sqleditor.common.ApiException;
import com.bocsoft.sqleditor.config.SqlEditorProperties;
import com.bocsoft.sqleditor.datasource.connection.ConnectionConfiguration;
import com.bocsoft.sqleditor.engine.gbase8a.Gbase8aEngineSupport;
import com.bocsoft.sqleditor.engine.hive.HiveEngineSupport;
import com.bocsoft.sqleditor.engine.hive_kerberos.DisabledKerberosHiveConnector;
import com.bocsoft.sqleditor.engine.hive_kerberos.HiveKerberosEngineSupport;
import com.bocsoft.sqleditor.engine.iceberg.IcebergEngineSupport;
import com.bocsoft.sqleditor.engine.mysql.MysqlEngineSupport;
import com.bocsoft.sqleditor.engine.postgres.PostgresEngineSupport;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;

class EngineRegistryTest {
    private final EngineRegistry registry = new EngineRegistry(Arrays.<EngineSupport>asList(
        new MysqlEngineSupport(), new PostgresEngineSupport(),
        new Gbase8aEngineSupport(new MysqlEngineSupport()), new HiveEngineSupport(),
        new HiveKerberosEngineSupport(new DisabledKerberosHiveConnector(), new SqlEditorProperties()),
        new IcebergEngineSupport(new DisabledKerberosHiveConnector(), new SqlEditorProperties())));

    @Test void registersMysqlPostgresGbaseHiveKerberosAndIceberg() {
        assertThat(registry.registered(EngineId.MYSQL)).isTrue();
        assertThat(registry.registered(EngineId.POSTGRESQL)).isTrue();
        assertThat(registry.registered(EngineId.GBASE_8A)).isTrue();
        assertThat(registry.registered(EngineId.HIVE)).isTrue();
        assertThat(registry.registered(EngineId.HIVE_KERBEROS)).isTrue();
        assertThat(registry.registered(EngineId.ICEBERG)).isTrue();
        assertThat(registry.require(EngineId.HIVE).id()).isEqualTo(EngineId.HIVE);
        assertThat(registry.require(EngineId.HIVE_KERBEROS).id()).isEqualTo(EngineId.HIVE_KERBEROS);
        assertThat(registry.require(EngineId.ICEBERG).id()).isEqualTo(EngineId.ICEBERG);
    }

    @Test void requireRejectsUnregisteredEngine() {
        assertThatThrownBy(() -> registry.require("ORACLE")).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.getCode()).isEqualTo("VALIDATION_FAILED");
        });
        assertThatThrownBy(() -> registry.require("GBASE_8C")).isInstanceOfSatisfying(ApiException.class, e -> {
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
