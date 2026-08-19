package com.bocsoft.sqleditor.engine.mysql;

import com.bocsoft.sqleditor.common.ApiException;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class MysqlSqlScannerTest {
    private final MysqlEngineSupport engine = new MysqlEngineSupport();

    @Test void ignoresDelimitersInsideQuotesAndComments() {
        assertThat(engine.split("select ';'; -- ;\n select `a;b` from t")).containsExactly("select ';'","-- ;\n select `a;b` from t");
    }

    @Test void rejectsMultipleAndUnclosedStatements() {
        assertThatThrownBy(() -> engine.requireSingle("select 1;select 2")).isInstanceOf(ApiException.class).extracting("code").isEqualTo("MULTI_STATEMENT_NOT_SUPPORTED");
        assertThatThrownBy(() -> engine.requireSingle("select 'x")).isInstanceOf(ApiException.class);
    }
}
