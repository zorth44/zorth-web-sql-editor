package com.bocsoft.sqleditor.engine.mysql;

import static org.assertj.core.api.Assertions.assertThat;

import com.bocsoft.sqleditor.engine.ExplainMode;
import com.bocsoft.sqleditor.engine.PlanEvidence;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class MysqlExplainTest {
    private final MysqlEngineSupport engine = new MysqlEngineSupport();

    @Test void rewritesSelectAndExistingExplainWithoutAnalyze() {
        assertThat(engine.rewriteExplain("SELECT 1", ExplainMode.PLAN)).isEqualTo("EXPLAIN SELECT 1");
        assertThat(engine.rewriteExplain("EXPLAIN SELECT 1", ExplainMode.PLAN)).isEqualTo("EXPLAIN SELECT 1");
        assertThat(engine.isAnalyzedExplain("SELECT 1")).isFalse();
        assertThat(engine.isAnalyzedExplain("EXPLAIN SELECT 1")).isFalse();
        assertThat(engine.isAnalyzedExplain("EXPLAIN ANALYZE SELECT 1")).isTrue();
        assertThat(engine.rewriteExplain("SELECT 1", ExplainMode.ANALYZE)).isEqualTo("EXPLAIN ANALYZE SELECT 1");
    }

    @Test void normalizesTraditionalPlanRows() {
        List<String> columns = Arrays.asList("id", "select_type", "table", "type", "key", "rows", "Extra");
        List<List<Object>> rows = Collections.<List<Object>>singletonList(
            Arrays.<Object>asList(1, "SIMPLE", "order_item", "ALL", null, 100, "Using where"));
        PlanEvidence evidence = engine.normalizePlan(columns, rows);
        assertThat(evidence.isSupported()).isTrue();
        assertThat(evidence.getEstimatedRows()).isEqualTo(100L);
        assertThat(evidence.getFullScan()).isTrue();
        assertThat(evidence.getTables()).extracting("table").containsExactly("order_item");
        assertThat(evidence.getReason()).isNull();
    }
}
