package com.bocsoft.sqleditor.engine.mysql;

import static org.assertj.core.api.Assertions.assertThat;

import com.bocsoft.sqleditor.engine.EngineField;
import org.junit.jupiter.api.Test;

class MysqlJdbcPropertyFieldsTest {
    @Test void descriptorPropertyKeysMatchValidatorAllowList() {
        MysqlEngineSupport mysql = new MysqlEngineSupport();
        assertThat(mysql.descriptor().getPropertyFields()).extracting(EngineField::getName)
            .containsExactlyElementsOf(new MysqlJdbc().allowedPropertyKeys());
    }
}
