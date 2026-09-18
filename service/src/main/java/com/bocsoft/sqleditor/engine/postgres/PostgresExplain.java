package com.bocsoft.sqleditor.engine.postgres;

import com.bocsoft.sqleditor.common.ApiException;
import com.bocsoft.sqleditor.engine.ExplainMode;
import com.bocsoft.sqleditor.engine.SqlLead;
import java.util.Locale;

final class PostgresExplain {
    boolean isAnalyzedExplain(String sql) {
        if (!"EXPLAIN".equals(SqlLead.firstWord(sql))) return false;
        String rest = SqlLead.afterFirstWord(sql);
        if ("ANALYZE".equals(SqlLead.firstWord(rest))) return true;
        if (rest.startsWith("(")) {
            int end = SqlLead.matchingParen(rest);
            if (end < 0) return false;
            String options = rest.substring(1, end).toUpperCase(Locale.ROOT);
            for (String part : options.split(",")) {
                String token = part.trim();
                if (token.equals("ANALYZE") || token.startsWith("ANALYZE ") || token.startsWith("ANALYZE=")) {
                    return true;
                }
            }
        }
        return false;
    }

    String rewrite(String sql, ExplainMode mode) {
        String statement = strip(sql);
        if (statement.isEmpty()) {
            throw ApiException.validation("statement", "INVALID", "EXPLAIN 缺少要分析的语句");
        }
        if (mode == ExplainMode.ANALYZE) return "EXPLAIN (ANALYZE, FORMAT JSON) " + statement;
        return "EXPLAIN (FORMAT JSON) " + statement;
    }

    private String strip(String sql) {
        if (!"EXPLAIN".equals(SqlLead.firstWord(sql))) return SqlLead.skipTrivia(sql).trim();
        String rest = SqlLead.afterFirstWord(sql);
        while (true) {
            String word = SqlLead.firstWord(rest);
            if ("ANALYZE".equals(word) || "VERBOSE".equals(word)) {
                rest = SqlLead.afterFirstWord(rest);
                continue;
            }
            break;
        }
        if (rest.startsWith("(")) {
            int end = SqlLead.matchingParen(rest);
            if (end >= 0) rest = SqlLead.skipTrivia(rest.substring(end + 1));
        }
        return rest.trim();
    }
}
