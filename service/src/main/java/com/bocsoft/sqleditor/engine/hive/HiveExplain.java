package com.bocsoft.sqleditor.engine.hive;

import com.bocsoft.sqleditor.engine.ExplainMode;
import com.bocsoft.sqleditor.engine.ExplainSql;
import com.bocsoft.sqleditor.engine.PlanEvidence;
import java.util.List;

final class HiveExplain {
    boolean isAnalyzedExplain(String sql) {
        return false;
    }

    String rewriteExplain(String sql, ExplainMode mode) {
        String inner = ExplainSql.innerAfterExplain(sql, false);
        if (inner.isEmpty()) inner = sql.trim();
        return "EXPLAIN " + inner;
    }

    PlanEvidence normalizePlan(List<String> columnNames, List<List<Object>> rows) {
        return PlanEvidence.unsupported("当前引擎无法规范化 Hive 执行计划");
    }
}
