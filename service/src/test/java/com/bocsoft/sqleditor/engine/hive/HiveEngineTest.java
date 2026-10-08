package com.bocsoft.sqleditor.engine.hive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bocsoft.sqleditor.common.ApiException;
import com.bocsoft.sqleditor.engine.ConnectionFailure;
import com.bocsoft.sqleditor.engine.EngineField;
import com.bocsoft.sqleditor.engine.EngineId;
import com.bocsoft.sqleditor.engine.ExplainMode;
import com.bocsoft.sqleditor.metadata.api.TableConstraintLimits;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.sql.SQLException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HiveEngineTest {
    private final HiveEngineSupport hive = new HiveEngineSupport();

    @Test void declaresHiveWireFamilyAndCatalogDescriptor() {
        assertThat(hive.id()).isEqualTo(EngineId.HIVE);
        assertThat(hive.family()).isEqualTo("HIVE_WIRE");
        assertThat(hive.defaultNamespaceRequired()).isFalse();
        assertThat(hive.canSwitchNamespaceOnConnection()).isTrue();
        assertThat(hive.identifierMaxLength()).isEqualTo(128);

        assertThat(hive.descriptor().getDisplayName()).isEqualTo("Hive");
        assertThat(hive.descriptor().getFamily()).isEqualTo("HIVE_WIRE");
        assertThat(hive.descriptor().getDefaultPort()).isEqualTo(10000);
        assertThat(hive.descriptor().getEditorLanguage()).isEqualTo("hive");
        assertThat(hive.descriptor().getIdentifierQuote()).isEqualTo("`");
        assertThat(hive.descriptor().getCapabilities().isDefaultNamespaceRequired()).isFalse();
        assertThat(hive.descriptor().getConnectionFields().get(1).getName()).isEqualTo("port");
        assertThat(hive.descriptor().getConnectionFields().get(1).getDefaultValue()).isEqualTo("10000");
        assertThat(hive.descriptor().getConnectionFields().get(4).getName()).isEqualTo("defaultDatabase");
        assertThat(hive.descriptor().getConnectionFields().get(4).getKind()).isEqualTo("DEFAULT_NAMESPACE");
        assertThat(hive.descriptor().getConnectionFields().get(4).getLabel()).isEqualTo("默认数据库");
        assertThat(hive.descriptor().getConnectionFields().get(4).isRequired()).isFalse();
        assertThat(hive.descriptor().getResourceTree().get(0).getKind()).isEqualTo("NAMESPACE");
        assertThat(hive.descriptor().getResourceTree().get(0).getLabel()).isEqualTo("数据库");
        assertThat(hive.descriptor().getResourceTree().get(0).getListEndpoint()).isEqualTo("databases");
    }

    @Test void descriptorPropertyFieldsMatchRuntimeAllowList() {
        assertThat(new HiveJdbc().allowedPropertyKeys()).containsExactly("hive.metastore.uris");
        assertThat(hive.descriptor().getPropertyFields()).extracting(EngineField::getName)
            .containsExactlyElementsOf(new HiveJdbc().allowedPropertyKeys());
    }

    @Test void acceptsOnlyThriftMetastoreUris() {
        assertThat(hive.validateProperties(Collections.singletonMap("hive.metastore.uris", "thrift://metastore.internal:9083")))
            .containsEntry("hive.metastore.uris", "thrift://metastore.internal:9083");
        assertThat(hive.validateProperties(Collections.singletonMap("hive.metastore.uris",
            "thrift://a.internal:9083,thrift://b.internal:9083")))
            .containsEntry("hive.metastore.uris", "thrift://a.internal:9083,thrift://b.internal:9083");
        assertThat(hive.validateProperties(Collections.singletonMap("hive.metastore.uris", "")))
            .containsEntry("hive.metastore.uris", "");
    }

    @Test void rejectsUnsafeHiveProperties() {
        assertThatThrownBy(() -> hive.validateProperties(Collections.singletonMap("serverTimezone", "UTC")))
            .isInstanceOf(ApiException.class).extracting("code").isEqualTo("VALIDATION_FAILED");
        assertThatThrownBy(() -> hive.validateProperties(Collections.singletonMap("useSSL", "true")))
            .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> hive.validateProperties(Collections.singletonMap("user", "attacker")))
            .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> hive.validateProperties(Collections.singletonMap("hive.metastore.uris", "http://metastore:9083")))
            .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> hive.validateProperties(Collections.singletonMap(
            "hive.metastore.uris", "thrift://a:9083; DROP DATABASE x")))
            .isInstanceOf(ApiException.class);
    }

    @Test void missingHiveDriverIsSanitizedConnectionFailure() {
        ConnectionFailure classNotFound = hive.classifyConnectionFailure(new ClassNotFoundException(HiveJdbc.DRIVER_CLASS));
        assertThat(classNotFound.getCode()).isEqualTo("CONNECTION_FAILED");
        assertThat(classNotFound.getMessage()).doesNotContain("jdbc:hive2").doesNotContain(HiveJdbc.DRIVER_CLASS);

        ConnectionFailure noSuitable = hive.classifyConnectionFailure(
            new SQLException("No suitable driver found for jdbc:hive2://secret-host:10000/db"));
        assertThat(noSuitable.getCode()).isEqualTo("CONNECTION_FAILED");
        assertThat(noSuitable.getMessage()).doesNotContain("jdbc:hive2").doesNotContain("secret-host");
    }

    @Test void classifiesAuthenticationDatabaseTimeoutAndRefused() {
        assertThat(hive.classifyConnectionFailure(new SQLException("Access denied for user", "28000")).getCode())
            .isEqualTo("AUTHENTICATION_FAILED");
        assertThat(hive.classifyConnectionFailure(
            new SQLException("Error while compiling statement: FAILED: SemanticException [Error 10072]: Database does not exist: missing", "42000"))
            .getCode()).isEqualTo("DATABASE_NOT_FOUND");
        assertThat(hive.classifyConnectionFailure(new ConnectException("secret target")).getCode())
            .isEqualTo("CONNECTION_REFUSED");
        assertThat(hive.classifyConnectionFailure(new SocketTimeoutException("secret target")).getCode())
            .isEqualTo("CONNECTION_TIMEOUT");
        assertThat(hive.classifyConnectionFailure(new SQLException("anything secret")).getMessage())
            .doesNotContain("anything").doesNotContain("secret");
    }

    @Test void jdbcUrlWithoutNamespaceKeepsSchemeHostAndPort() {
        assertThat(hive.jdbcUrlWithoutNamespace("jdbc:hive2://10.0.0.1:10000/orders"))
            .isEqualTo("jdbc:hive2://10.0.0.1:10000/");
        assertThat(hive.jdbcUrlWithoutNamespace("jdbc:hive2://[2001:db8::5]:10000/orders"))
            .isEqualTo("jdbc:hive2://[2001:db8::5]:10000/");
        assertThat(hive.jdbcUrlWithoutNamespace("jdbc:hive2://10.0.0.1:10000"))
            .isEqualTo("jdbc:hive2://10.0.0.1:10000");
    }

    @Test void relationshipMetadataIsExplicitlyUnavailable() {
        assertThat(hive.supportsRelationshipMetadata()).isFalse();
        assertThat(hive.tableConstraints(null, "analytics", "orders", new TableConstraintLimits(8, 8, 8))
            .getUniqueKeys().getCoverage()).isEqualTo("UNAVAILABLE");
        assertThat(hive.tableConstraints(null, "analytics", "orders", new TableConstraintLimits(8, 8, 8))
            .getForeignKeys().getCoverage()).isEqualTo("UNAVAILABLE");
        assertThatThrownBy(() -> hive.importedRelationships(null, "analytics", "orders",
            Collections.<String>emptySet(), 10))
            .isInstanceOf(ApiException.class).extracting("code").isEqualTo("CAPABILITY_NOT_SUPPORTED");
    }

    @Test void scannerAndExplainFollowHiveSyntax() {
        assertThat(hive.split("select `a;b` from t; select 2"))
            .containsExactly("select `a;b` from t", "select 2");
        assertThatThrownBy(() -> hive.requireSingle("select 1; select 2"))
            .isInstanceOf(ApiException.class).extracting("code").isEqualTo("MULTI_STATEMENT_NOT_SUPPORTED");
        assertThat(hive.isAnalyzedExplain("explain analyze select 1")).isFalse();
        assertThat(hive.rewriteExplain("select 1", ExplainMode.PLAN)).isEqualTo("EXPLAIN select 1");
    }
}
