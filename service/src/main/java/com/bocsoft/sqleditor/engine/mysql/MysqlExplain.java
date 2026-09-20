package com.bocsoft.sqleditor.engine.mysql;

import com.bocsoft.sqleditor.engine.ExplainMode;
import com.bocsoft.sqleditor.engine.ExplainSql;
import com.bocsoft.sqleditor.engine.PlanEvidence;
import com.bocsoft.sqleditor.engine.PlanFinding;
import com.bocsoft.sqleditor.engine.TablePlanEntry;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class MysqlExplain {
    boolean isAnalyzedExplain(String sql) {
        return ExplainSql.optionsContainAnalyze(sql, false);
    }

    String rewriteExplain(String sql, ExplainMode mode) {
        String inner = ExplainSql.innerAfterExplain(sql, false);
        if (inner.isEmpty()) inner = sql.trim();
        if (mode == ExplainMode.ANALYZE) return "EXPLAIN ANALYZE " + inner;
        return "EXPLAIN " + inner;
    }

    PlanEvidence normalizePlan(List<String> columns, List<List<Object>> rows) {
        if (columns == null || rows == null || rows.isEmpty()) {
            return PlanEvidence.unsupported("执行计划没有可规范化的行");
        }
        int tableIdx = index(columns, "table");
        int typeIdx = index(columns, "type");
        int keyIdx = index(columns, "key");
        int rowsIdx = index(columns, "rows");
        int extraIdx = index(columns, "extra");
        if (tableIdx < 0 && typeIdx < 0 && rowsIdx < 0) {
            return PlanEvidence.unsupported("当前 MySQL 计划格式缺少可移植字段");
        }
        List<TablePlanEntry> tables = new ArrayList<TablePlanEntry>();
        Set<String> indexes = new LinkedHashSet<String>();
        Long totalRows = null;
        Boolean fullScan = Boolean.FALSE;
        for (List<Object> row : rows) {
            String table = text(row, tableIdx);
            String access = text(row, typeIdx);
            String key = text(row, keyIdx);
            Long estimated = number(row, rowsIdx);
            List<String> used = new ArrayList<String>();
            if (key != null && !key.isEmpty() && !"NULL".equalsIgnoreCase(key)) {
                used.add(key);
                indexes.add(key);
            }
            if (isFullScan(access, text(row, extraIdx))) fullScan = Boolean.TRUE;
            if (estimated != null) totalRows = totalRows == null ? estimated : Long.valueOf(totalRows.longValue() + estimated.longValue());
            if (table != null && !table.isEmpty()) {
                tables.add(new TablePlanEntry(table, access, estimated, used));
            }
        }
        List<PlanFinding> findings = new ArrayList<PlanFinding>();
        if (Boolean.TRUE.equals(fullScan)) {
            findings.add(new PlanFinding("FULL_SCAN", "计划中存在全表扫描证据"));
        }
        return new PlanEvidence(true, totalRows, fullScan, new ArrayList<String>(indexes), tables, findings, null);
    }

    private boolean isFullScan(String access, String extra) {
        String type = access == null ? "" : access.toUpperCase(Locale.ROOT);
        if ("ALL".equals(type)) return true;
        return extra != null && extra.toUpperCase(Locale.ROOT).contains("USING WHERE") && "INDEX".equals(type) == false && type.isEmpty();
    }

    private int index(List<String> columns, String name) {
        for (int i = 0; i < columns.size(); i++) {
            if (columns.get(i) != null && columns.get(i).equalsIgnoreCase(name)) return i;
        }
        return -1;
    }

    private String text(List<Object> row, int index) {
        if (index < 0 || index >= row.size() || row.get(index) == null) return null;
        String value = String.valueOf(row.get(index)).trim();
        return value.isEmpty() || "null".equalsIgnoreCase(value) ? null : value;
    }

    private Long number(List<Object> row, int index) {
        if (index < 0 || index >= row.size() || row.get(index) == null) return null;
        Object value = row.get(index);
        if (value instanceof Number) return Long.valueOf(((Number) value).longValue());
        try {
            return Long.valueOf(String.valueOf(value).trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
