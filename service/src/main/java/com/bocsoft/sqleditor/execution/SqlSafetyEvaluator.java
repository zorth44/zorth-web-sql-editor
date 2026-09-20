package com.bocsoft.sqleditor.execution;

import com.bocsoft.sqleditor.common.ApiException;
import com.bocsoft.sqleditor.engine.EngineSupport;
import com.bocsoft.sqleditor.engine.ExplainSql;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class SqlSafetyEvaluator {
    private final SqlStatementClassifier classifier;

    public SqlSafetyEvaluator(SqlStatementClassifier classifier) {
        this.classifier = classifier;
    }

    public SqlSafetyAssessment evaluate(EngineSupport engine, String sql) {
        List<SqlFinding> violations = new ArrayList<SqlFinding>();
        List<SqlFinding> warnings = new ArrayList<SqlFinding>();
        List<String> statements;
        try {
            statements = engine.split(sql);
        } catch (ApiException e) {
            boolean multi = "MULTI_STATEMENT_NOT_SUPPORTED".equals(e.getCode());
            violations.add(new SqlFinding(multi ? "MULTI_STATEMENT_NOT_SUPPORTED" : "INVALID_SQL",
                e.getMessage() == null ? "SQL 不合法" : e.getMessage()));
            return new SqlSafetyAssessment(false, StatementType.OTHER.name(), false, multi,
                new ArrayList<SqlTableRef>(), warnings, violations, sql);
        }
        if (statements.isEmpty()) {
            violations.add(new SqlFinding("STATEMENT_REQUIRED", "SQL 不能为空"));
            return new SqlSafetyAssessment(false, StatementType.OTHER.name(), false, false,
                new ArrayList<SqlTableRef>(), warnings, violations, sql);
        }
        if (statements.size() != 1) {
            violations.add(new SqlFinding("MULTI_STATEMENT_NOT_SUPPORTED", "暂不支持批量执行"));
            return new SqlSafetyAssessment(false, StatementType.OTHER.name(), false, true,
                new ArrayList<SqlTableRef>(), warnings, violations, sql);
        }
        String single = statements.get(0);
        StatementType type = classifier.classify(single);
        boolean analyzed = engine.isAnalyzedExplain(single);
        boolean agentSelect = isSupportedAgentSelect(single, type);
        if (analyzed) {
            violations.add(new SqlFinding("EXPLAIN_ANALYZE_NOT_ALLOWED", "不允许 ANALYZE 执行计划"));
        }
        if (type != StatementType.SELECT) {
            violations.add(new SqlFinding("READ_ONLY_VIOLATION", "只读模式只允许查询语句"));
        } else if (!agentSelect) {
            violations.add(new SqlFinding("EXPLAIN_STATEMENT_NOT_SUPPORTED", "仅支持单条只读 SELECT"));
        }
        List<SqlTableRef> tables = extractTables(single);
        boolean valid = violations.isEmpty();
        return new SqlSafetyAssessment(valid, type.name(), type == StatementType.SELECT && !analyzed && statements.size() == 1,
            false, tables, warnings, violations, single);
    }

    public String requireSafeSelect(EngineSupport engine, String sql) {
        SqlSafetyAssessment assessment = evaluate(engine, sql);
        if (assessment.isValid()) return assessment.getSql();
        SqlFinding first = assessment.getViolations().get(0);
        HttpStatus status = "MULTI_STATEMENT_NOT_SUPPORTED".equals(first.getCode())
            ? HttpStatus.BAD_REQUEST : HttpStatus.UNPROCESSABLE_ENTITY;
        throw new ApiException(status, first.getCode(), first.getMessage());
    }

    private boolean isSupportedAgentSelect(String sql, StatementType type) {
        if (type != StatementType.SELECT) return false;
        String keyword = ExplainSql.firstKeyword(sql);
        return "SELECT".equalsIgnoreCase(keyword) || "WITH".equalsIgnoreCase(keyword);
    }

    List<SqlTableRef> extractTables(String sql) {
        List<SqlTableRef> out = new ArrayList<SqlTableRef>();
        String upper = sql.toUpperCase(Locale.ROOT);
        int from = indexOfKeyword(upper, "FROM");
        if (from < 0) return out;
        String ident = nextIdentifier(sql, from + 4);
        if (ident != null) out.add(parseTable(ident));
        int search = from + 4;
        while (true) {
            int join = indexOfKeyword(upper.substring(search), "JOIN");
            if (join < 0) break;
            int abs = search + join;
            String table = nextIdentifier(sql, abs + 4);
            if (table != null) out.add(parseTable(table));
            search = abs + 4;
        }
        return out;
    }

    private int indexOfKeyword(String upper, String keyword) {
        int i = 0;
        while (i < upper.length()) {
            int found = upper.indexOf(keyword, i);
            if (found < 0) return -1;
            boolean startOk = found == 0 || !Character.isLetterOrDigit(upper.charAt(found - 1));
            int end = found + keyword.length();
            boolean endOk = end >= upper.length() || !Character.isLetterOrDigit(upper.charAt(end));
            if (startOk && endOk) return found;
            i = found + 1;
        }
        return -1;
    }

    private String nextIdentifier(String sql, int from) {
        int i = ExplainSql.skipTrivia(sql, from);
        if (i >= sql.length()) return null;
        char c = sql.charAt(i);
        if (c == '`' || c == '"' || c == '\'') {
            int end = sql.indexOf(c, i + 1);
            if (end < 0) return null;
            String first = sql.substring(i, end + 1);
            int next = ExplainSql.skipTrivia(sql, end + 1);
            if (next < sql.length() && sql.charAt(next) == '.') {
                String second = nextIdentifier(sql, next + 1);
                return second == null ? unquote(first) : unquote(first) + "." + second;
            }
            return unquote(first);
        }
        int j = i;
        while (j < sql.length() && (Character.isLetterOrDigit(sql.charAt(j)) || sql.charAt(j) == '_' || sql.charAt(j) == '.')) j++;
        if (j == i) return null;
        return sql.substring(i, j);
    }

    private String unquote(String value) {
        if (value.length() >= 2) {
            char c = value.charAt(0);
            if ((c == '`' || c == '"' || c == '\'') && value.charAt(value.length() - 1) == c) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }

    private SqlTableRef parseTable(String ident) {
        int dot = ident.lastIndexOf('.');
        if (dot < 0) return new SqlTableRef(null, null, ident);
        return new SqlTableRef(null, ident.substring(0, dot), ident.substring(dot + 1));
    }
}
