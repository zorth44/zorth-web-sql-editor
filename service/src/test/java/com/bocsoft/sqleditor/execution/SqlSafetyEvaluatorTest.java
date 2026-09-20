package com.bocsoft.sqleditor.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bocsoft.sqleditor.common.ApiException;
import com.bocsoft.sqleditor.engine.mysql.MysqlEngineSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SqlSafetyEvaluatorTest {
    private final SqlSafetyEvaluator evaluator = new SqlSafetyEvaluator(new SqlStatementClassifier());
    private final MysqlEngineSupport engine = new MysqlEngineSupport();

    @Test void acceptsSingleSelect() {
        SqlSafetyAssessment assessment = evaluator.evaluate(engine, "SELECT id FROM orders");
        assertThat(assessment.isValid()).isTrue();
        assertThat(assessment.getStatementType()).isEqualTo("SELECT");
        assertThat(assessment.isReadOnly()).isTrue();
        assertThat(assessment.isMultiStatement()).isFalse();
        assertThat(assessment.getTables()).extracting("name").contains("orders");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "INSERT INTO t VALUES (1)",
        "UPDATE t SET a=1",
        "DELETE FROM t",
        "DROP TABLE t",
        "CREATE TABLE t (id int)",
        "EXPLAIN ANALYZE SELECT 1",
        "SHOW TABLES",
        "SELECT 1; SELECT 2"
    })
    void rejectsUnsafeSqlWithoutBorrowing(String sql) {
        SqlSafetyAssessment assessment = evaluator.evaluate(engine, sql);
        assertThat(assessment.isValid()).isFalse();
        assertThat(assessment.getViolations()).isNotEmpty();
        assertThatThrownBy(() -> evaluator.requireSafeSelect(engine, sql)).isInstanceOf(ApiException.class);
    }
}
