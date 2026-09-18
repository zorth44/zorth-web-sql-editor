package com.bocsoft.sqleditor.engine.mysql;

import static org.assertj.core.api.Assertions.assertThat;

import com.bocsoft.sqleditor.engine.ExplainMode;
import org.junit.jupiter.api.Test;

class MysqlExplainTest {
    private final MysqlEngineSupport engine = new MysqlEngineSupport();

    @Test
    void rewritesSelectToPlanOnlyExplain() {
        assertThat(engine.rewriteExplain("SELECT 1", ExplainMode.PLAN)).isEqualTo("EXPLAIN SELECT 1");
        assertThat(engine.rewriteExplain("EXPLAIN SELECT 1", ExplainMode.PLAN)).isEqualTo("EXPLAIN SELECT 1");
        assertThat(engine.rewriteExplain("EXPLAIN FORMAT=JSON SELECT 1", ExplainMode.PLAN)).isEqualTo("EXPLAIN SELECT 1");
        assertThat(engine.isAnalyzedExplain("EXPLAIN SELECT 1")).isFalse();
        assertThat(engine.rewriteExplain("SELECT 1", ExplainMode.PLAN)).doesNotContain("ANALYZE");
    }

    @Test
    void detectsAndRewritesAnalyzedExplain() {
        assertThat(engine.isAnalyzedExplain("EXPLAIN ANALYZE SELECT 1")).isTrue();
        assertThat(engine.isAnalyzedExplain("  /* c */ EXPLAIN ANALYZE SELECT 1")).isTrue();
        assertThat(engine.rewriteExplain("SELECT 1", ExplainMode.ANALYZE)).isEqualTo("EXPLAIN ANALYZE SELECT 1");
        assertThat(engine.rewriteExplain("EXPLAIN ANALYZE SELECT 1", ExplainMode.ANALYZE)).isEqualTo("EXPLAIN ANALYZE SELECT 1");
    }
}
