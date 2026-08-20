package com.bocsoft.sqleditor.engine.gbase8a;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bocsoft.sqleditor.common.ApiException;
import com.bocsoft.sqleditor.engine.EngineField;
import com.bocsoft.sqleditor.engine.EngineId;
import com.bocsoft.sqleditor.engine.mysql.MysqlEngineSupport;
import java.sql.SQLException;
import java.util.Collections;
import org.junit.jupiter.api.Test;

class Gbase8aEngineTest {
    private final Gbase8aEngineSupport gbase = new Gbase8aEngineSupport(new MysqlEngineSupport());

    @Test void hangsOnMysqlWireFamily() {
        assertThat(gbase.id()).isEqualTo(EngineId.GBASE_8A);
        assertThat(gbase.family()).isEqualTo("MYSQL_WIRE");
        assertThat(gbase.defaultNamespaceRequired()).isFalse();
        assertThat(gbase.descriptor().getDisplayName()).isEqualTo("GBase 8a");
        assertThat(gbase.descriptor().getDefaultPort()).isEqualTo(5258);
        assertThat(gbase.descriptor().getEditorLanguage()).isEqualTo("mysql");
        assertThat(gbase.descriptor().getIdentifierQuote()).isEqualTo("`");
        assertThat(gbase.descriptor().getConnectionFields().get(1).getDefaultValue()).isEqualTo("5258");
        assertThat(gbase.descriptor().getConnectionFields().get(4).getName()).isEqualTo("defaultDatabase");
        assertThat(gbase.descriptor().getConnectionFields().get(4).isRequired()).isFalse();
        assertThat(gbase.descriptor().getResourceTree().get(0).getKind()).isEqualTo("NAMESPACE");
        assertThat(gbase.descriptor().getResourceTree().get(0).getLabel()).isEqualTo("数据库");
        assertThat(gbase.descriptor().getResourceTree().get(0).getListEndpoint()).isEqualTo("databases");
        assertThat(gbase.descriptor().getPropertyFields()).extracting(EngineField::getName)
            .containsExactly("serverTimezone", "characterSetResults", "zeroDateTimeBehavior", "tinyInt1isBit", "sendFractionalSeconds");
    }

    @Test void rejectsPostgresKeysAndAllowsMysqlKeys() {
        assertThat(gbase.validateProperties(Collections.singletonMap("serverTimezone", "UTC")))
            .containsEntry("serverTimezone", "UTC");
        assertThatThrownBy(() -> gbase.validateProperties(Collections.singletonMap("ApplicationName", "x")))
            .isInstanceOf(ApiException.class);
    }

    @Test void rewritesMysqlFamilyUrlToOfficialGbaseScheme() {
        assertThat(gbase.jdbcUrlWithoutNamespace("jdbc:mysql://10.0.0.1:5258/orders"))
            .isEqualTo("jdbc:gbase://10.0.0.1:5258/");
        assertThat(gbase.jdbcUrlWithoutNamespace("jdbc:gbase://[2001:db8::5]:5258/orders"))
            .isEqualTo("jdbc:gbase://[2001:db8::5]:5258/");
    }

    @Test void classifiesMissingOfficialDriverWithoutLeakingUrl() {
        assertThat(gbase.classifyConnectionFailure(new ClassNotFoundException(Gbase8aJdbc.DRIVER_CLASS)).getCode())
            .isEqualTo("CONNECTION_FAILED");
        assertThat(gbase.classifyConnectionFailure(new SQLException("No suitable driver found for jdbc:gbase://secret:5258/db")).getMessage())
            .isEqualTo("未找到 GBase 8a 官方 JDBC 驱动")
            .doesNotContain("jdbc:gbase")
            .doesNotContain("secret");
    }
}
