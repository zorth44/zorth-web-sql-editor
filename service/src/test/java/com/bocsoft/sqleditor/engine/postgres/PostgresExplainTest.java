package com.bocsoft.sqleditor.engine.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import com.bocsoft.sqleditor.engine.ExplainMode;
import com.bocsoft.sqleditor.engine.PlanEvidence;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class PostgresExplainTest {
    private final PostgresEngineSupport engine = new PostgresEngineSupport();

    @Test void rewritesSelectAndDetectsAnalyzeVariants() {
        assertThat(engine.rewriteExplain("SELECT 1", ExplainMode.PLAN)).isEqualTo("EXPLAIN (FORMAT JSON) SELECT 1");
        assertThat(engine.rewriteExplain("EXPLAIN SELECT 1", ExplainMode.PLAN)).isEqualTo("EXPLAIN (FORMAT JSON) SELECT 1");
        assertThat(engine.rewriteExplain("SELECT 1", ExplainMode.ANALYZE))
            .isEqualTo("EXPLAIN (ANALYZE, FORMAT JSON) SELECT 1");
        assertThat(engine.isAnalyzedExplain("EXPLAIN ANALYZE SELECT 1")).isTrue();
        assertThat(engine.isAnalyzedExplain("EXPLAIN (ANALYZE, FORMAT JSON) SELECT 1")).isTrue();
        assertThat(engine.isAnalyzedExplain("EXPLAIN (FORMAT JSON) SELECT 1")).isFalse();
        assertThat(engine.isAnalyzedExplain("SELECT $tag$ hello; world $tag$")).isFalse();
    }

    @Test void normalizesJsonPlan() {
        String json = "[{\"Plan\":{\"Node Type\":\"Seq Scan\",\"Relation Name\":\"order_item\",\"Plan Rows\":10}}]";
        PlanEvidence evidence = engine.normalizePlan(Collections.singletonList("QUERY PLAN"),
            Collections.<List<Object>>singletonList(Collections.<Object>singletonList(json)));
        assertThat(evidence.isSupported()).isTrue();
        assertThat(evidence.getEstimatedRows()).isEqualTo(10L);
        assertThat(evidence.getFullScan()).isTrue();
        assertThat(evidence.getTables()).extracting("table").containsExactly("order_item");
    }
}
