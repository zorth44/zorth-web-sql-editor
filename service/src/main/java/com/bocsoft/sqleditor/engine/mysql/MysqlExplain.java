package com.bocsoft.sqleditor.engine.mysql;

import com.bocsoft.sqleditor.common.ApiException;
import com.bocsoft.sqleditor.engine.ExplainMode;
import com.bocsoft.sqleditor.engine.SqlLead;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class MysqlExplain {
    private static final Pattern PREFIX = Pattern.compile(
        "(?is)^EXPLAIN(?:\\s+ANALYZE)?(?:\\s+FORMAT\\s*=\\s*\\S+)?\\s+");

    boolean isAnalyzedExplain(String sql) {
        if (!"EXPLAIN".equals(SqlLead.firstWord(sql))) return false;
        String rest = SqlLead.afterFirstWord(sql);
        return "ANALYZE".equals(SqlLead.firstWord(rest));
    }

    String rewrite(String sql, ExplainMode mode) {
        String statement = strip(sql);
        if (statement.isEmpty()) {
            throw ApiException.validation("statement", "INVALID", "EXPLAIN 缺少要分析的语句");
        }
        if (mode == ExplainMode.ANALYZE) return "EXPLAIN ANALYZE " + statement;
        return "EXPLAIN " + statement;
    }

    private String strip(String sql) {
        String rest = SqlLead.skipTrivia(sql);
        Matcher matcher = PREFIX.matcher(rest);
        if (matcher.find()) return rest.substring(matcher.end()).trim();
        return rest.trim();
    }
}
