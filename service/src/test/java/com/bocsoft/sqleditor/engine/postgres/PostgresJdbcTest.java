package com.bocsoft.sqleditor.engine.postgres;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bocsoft.sqleditor.common.ApiException;
import com.bocsoft.sqleditor.engine.EngineField;
import com.bocsoft.sqleditor.engine.EngineId;
import java.sql.SQLException;
import java.util.Collections;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class PostgresJdbcTest {
    private final PostgresEngineSupport postgres = new PostgresEngineSupport();

    @Test void descriptorPropertyKeysMatchValidatorAllowList() {
        assertThat(postgres.descriptor().getPropertyFields()).extracting(EngineField::getName)
            .containsExactlyElementsOf(new PostgresJdbc().allowedPropertyKeys());
    }

    @Test void descriptorDeclaresSchemaAsNamespace() {
        assertThat(postgres.id()).isEqualTo(EngineId.POSTGRESQL);
        assertThat(postgres.descriptor().getFamily()).isEqualTo("POSTGRES_WIRE");
        assertThat(postgres.descriptor().getDefaultPort()).isEqualTo(5432);
        assertThat(postgres.descriptor().getEditorLanguage()).isEqualTo("pgsql");
        assertThat(postgres.descriptor().getIdentifierQuote()).isEqualTo("\"");
        assertThat(postgres.descriptor().getCapabilities().isDefaultNamespaceRequired()).isTrue();
        assertThat(postgres.descriptor().getConnectionFields().get(4).getName()).isEqualTo("defaultDatabase");
        assertThat(postgres.descriptor().getConnectionFields().get(4).getLabel()).isEqualTo("数据库名");
        assertThat(postgres.descriptor().getConnectionFields().get(4).isRequired()).isTrue();
        assertThat(postgres.descriptor().getResourceTree().get(0).getKind()).isEqualTo("NAMESPACE");
        assertThat(postgres.descriptor().getResourceTree().get(0).getLabel()).isEqualTo("模式");
        assertThat(postgres.descriptor().getResourceTree().get(0).getListEndpoint()).isEqualTo("databases");
    }

    @Test void jdbcUrlKeepsPinnedDatabase() {
        assertThat(postgres.jdbcUrlWithoutNamespace("jdbc:postgresql://127.0.0.1:5432/orders"))
            .isEqualTo("jdbc:postgresql://127.0.0.1:5432/orders");
    }

    @Test void rejectsMysqlKeysAndCurrentSchema() {
        assertThatThrownBy(() -> postgres.validateProperties(Collections.singletonMap("serverTimezone", "UTC")))
            .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> postgres.validateProperties(Collections.singletonMap("currentSchema", "public")))
            .isInstanceOf(ApiException.class);
    }

    @Test void classifiesAuthAndMissingDatabase() {
        assertThat(postgres.classifyConnectionFailure(new SQLException("password authentication failed for user", "28P01"))
            .getCode()).isEqualTo("AUTHENTICATION_FAILED");
        assertThat(postgres.classifyConnectionFailure(new SQLException("database \"missing\" does not exist", "3D000"))
            .getCode()).isEqualTo("DATABASE_NOT_FOUND");
        assertThat(postgres.classifyConnectionFailure(new java.net.ConnectException("secret target")).getCode())
            .isEqualTo("CONNECTION_REFUSED");
        assertThat(postgres.classifyConnectionFailure(new SQLException("anything")).getMessage())
            .doesNotContain("anything").doesNotContain("jdbc:postgresql");
    }

    @Test void applyConnectTimeoutUsesSeconds() {
        Properties properties = new Properties();
        postgres.applyConnectTimeout(properties, 2500);
        assertThat(properties.getProperty("connectTimeout")).isEqualTo("3");
        assertThat(properties.getProperty("loginTimeout")).isEqualTo("3");
        assertThat(postgres.streamingFetchSize()).isEqualTo(100);
        assertThat(postgres.streamingRequiresAutoCommitOff()).isTrue();
    }

    @Test void splitIgnoresSemicolonInsideDollarQuotes() {
        assertThat(postgres.split("select $tag$ a;b $tag$; select 2"))
            .containsExactly("select $tag$ a;b $tag$", "select 2");
        assertThat(postgres.requireSingle("select $$ a;b $$")).isEqualTo("select $$ a;b $$");
        assertThatThrownBy(() -> postgres.requireSingle("select 1; select 2"))
            .isInstanceOf(ApiException.class).extracting("code").isEqualTo("MULTI_STATEMENT_NOT_SUPPORTED");
    }
}
