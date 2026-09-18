package com.bocsoft.sqleditor.engine.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import com.bocsoft.sqleditor.engine.ExplainMode;
import org.junit.jupiter.api.Test;

class PostgresExplainTest {
    private final PostgresEngineSupport engine = new PostgresEngineSupport();

    @Test
    void rewritesSelectToJsonPlan() {
        assertThat(engine.rewriteExplain("SELECT 1", ExplainMode.PLAN)).isEqualTo("EXPLAIN (FORMAT JSON) SELECT 1");
        assertThat(engine.rewriteExplain("EXPLAIN SELECT 1", ExplainMode.PLAN)).isEqualTo("EXPLAIN (FORMAT JSON) SELECT 1");
        assertThat(engine.rewriteExplain("EXPLAIN (FORMAT JSON) SELECT 1", ExplainMode.PLAN)).isEqualTo("EXPLAIN (FORMAT JSON) SELECT 1");
        assertThat(engine.isAnalyzedExplain("EXPLAIN (FORMAT JSON) SELECT 1")).isFalse();
        assertThat(engine.rewriteExplain("SELECT 1", ExplainMode.PLAN)).doesNotContain("ANALYZE");
    }

    @Test
    void detectsAnalyzeKeywordAndOptionList() {
        assertThat(engine.isAnalyzedExplain("EXPLAIN ANALYZE SELECT 1")).isTrue();
        assertThat(engine.isAnalyzedExplain("EXPLAIN (ANALYZE, FORMAT JSON) SELECT 1")).isTrue();
        assertThat(engine.rewriteExplain("SELECT 1", ExplainMode.ANALYZE))
            .isEqualTo("EXPLAIN (ANALYZE, FORMAT JSON) SELECT 1");
        assertThat(engine.rewriteExplain("EXPLAIN ANALYZE SELECT 1", ExplainMode.ANALYZE))
            .isEqualTo("EXPLAIN (ANALYZE, FORMAT JSON) SELECT 1");
    }

    @Test
    void requireSingleStillHonorsDollarQuotes() {
        assertThat(engine.requireSingle("SELECT $tag$ hello; world $tag$ AS v"))
            .contains("$tag$ hello; world $tag$");
    }
}
