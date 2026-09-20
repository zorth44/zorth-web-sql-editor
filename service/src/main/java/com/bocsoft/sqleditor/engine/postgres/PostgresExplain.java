package com.bocsoft.sqleditor.engine.postgres;

import com.bocsoft.sqleditor.engine.ExplainMode;
import com.bocsoft.sqleditor.engine.ExplainSql;
import com.bocsoft.sqleditor.engine.PlanEvidence;
import com.bocsoft.sqleditor.engine.PlanFinding;
import com.bocsoft.sqleditor.engine.TablePlanEntry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class PostgresExplain {
    private final ObjectMapper mapper = new ObjectMapper();

    boolean isAnalyzedExplain(String sql) {
        return ExplainSql.optionsContainAnalyze(sql, true);
    }

    String rewriteExplain(String sql, ExplainMode mode) {
        String inner = ExplainSql.innerAfterExplain(sql, true);
        if (inner.isEmpty()) inner = sql.trim();
        if (mode == ExplainMode.ANALYZE) return "EXPLAIN (ANALYZE, FORMAT JSON) " + inner;
        return "EXPLAIN (FORMAT JSON) " + inner;
    }

    PlanEvidence normalizePlan(List<String> columns, List<List<Object>> rows) {
        if (rows == null || rows.isEmpty() || rows.get(0) == null || rows.get(0).isEmpty()) {
            return PlanEvidence.unsupported("执行计划没有可规范化的行");
        }
        Object cell = rows.get(0).get(0);
        if (cell == null) return PlanEvidence.unsupported("PostgreSQL 计划为空");
        try {
            JsonNode root = mapper.readTree(String.valueOf(cell));
            JsonNode plan = root.isArray() && root.size() > 0 ? root.get(0).path("Plan") : root.path("Plan");
            if (plan.isMissingNode() || plan.isNull()) {
                return PlanEvidence.unsupported("PostgreSQL 计划缺少 Plan 节点");
            }
            List<TablePlanEntry> tables = new ArrayList<TablePlanEntry>();
            Set<String> indexes = new LinkedHashSet<String>();
            boolean[] fullScan = new boolean[] { false };
            collect(plan, tables, indexes, fullScan);
            Long estimated = plan.has("Plan Rows") && plan.get("Plan Rows").isNumber()
                ? Long.valueOf(plan.get("Plan Rows").asLong()) : null;
            List<PlanFinding> findings = new ArrayList<PlanFinding>();
            if (fullScan[0]) findings.add(new PlanFinding("FULL_SCAN", "计划中存在顺序扫描证据"));
            return new PlanEvidence(true, estimated, Boolean.valueOf(fullScan[0]),
                new ArrayList<String>(indexes), tables, findings, null);
        } catch (Exception e) {
            return PlanEvidence.unsupported("无法解析 PostgreSQL JSON 计划");
        }
    }

    private void collect(JsonNode node, List<TablePlanEntry> tables, Set<String> indexes, boolean[] fullScan) {
        if (node == null || !node.isObject()) return;
        String type = text(node, "Node Type");
        String relation = text(node, "Relation Name");
        String index = text(node, "Index Name");
        Long rows = node.has("Plan Rows") && node.get("Plan Rows").isNumber()
            ? Long.valueOf(node.get("Plan Rows").asLong()) : null;
        if (type != null && type.toUpperCase(Locale.ROOT).contains("SEQ SCAN")) fullScan[0] = true;
        List<String> used = new ArrayList<String>();
        if (index != null) {
            used.add(index);
            indexes.add(index);
        }
        if (relation != null) tables.add(new TablePlanEntry(relation, type, rows, used));
        JsonNode children = node.get("Plans");
        if (children != null && children.isArray()) {
            for (JsonNode child : children) collect(child, tables, indexes, fullScan);
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || !value.isValueNode()) return null;
        String text = value.asText();
        return text == null || text.trim().isEmpty() ? null : text;
    }
}
