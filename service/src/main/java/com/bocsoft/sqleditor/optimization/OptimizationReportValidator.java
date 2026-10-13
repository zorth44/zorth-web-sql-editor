package com.bocsoft.sqleditor.optimization;

import com.bocsoft.sqleditor.optimization.api.OptimizationReport;
import com.bocsoft.sqleditor.optimization.api.TaskSummary;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class OptimizationReportValidator {
    private final ObjectMapper json;
    public OptimizationReportValidator(ObjectMapper json) { this.json = json; }
    public OptimizationReport validate(JsonNode root) {
        try {
            require(root != null && root.isObject());
            text(root, "verdict");
            array(root, "issues"); array(root, "suggestions"); array(root, "limitations");
            Set<String> ids = new HashSet<String>();
            Set<String> issueIds = new HashSet<String>();
            for (JsonNode issue : root.get("issues")) {
                String id = text(issue, "id"); require(ids.add(id)); issueIds.add(id);
                integer(issue, "ruleId", false);
                String category = text(issue, "category");
                require("POLICY".equals(category) || "PERFORMANCE".equals(category));
                String priority = text(issue, "priority"); require("重要".equals(priority) || "一般".equals(priority));
                text(issue, "detail"); array(issue, "evidence");
                boolean sql = false, tool = false;
                for (JsonNode evidence : issue.get("evidence")) {
                    String kind = text(evidence, "kind"); require("sql".equals(kind) || "tool".equals(kind));
                    text(evidence, "content"); sql |= "sql".equals(kind); tool |= "tool".equals(kind);
                }
                require(issue.has("severity"));
                if ("POLICY".equals(category)) require(issue.get("severity").isNull() && sql);
                else {
                    String severity = text(issue, "severity");
                    require(("HIGH".equals(severity) || "MEDIUM".equals(severity) || "LOW".equals(severity)) && tool);
                }
            }
            for (JsonNode suggestion : root.get("suggestions")) {
                require(ids.add(text(suggestion, "id"))); text(suggestion, "detail"); array(suggestion, "issueIds");
                for (JsonNode id : suggestion.get("issueIds")) require(id.isTextual() && issueIds.contains(id.textValue()));
            }
            for (JsonNode limitation : root.get("limitations")) { integer(limitation, "ruleId", true); text(limitation, "reason"); }
            return json.treeToValue(root, OptimizationReport.class);
        } catch (Exception e) { throw new OptimizationFailure("REPORT_INVALID"); }
    }
    public TaskSummary summary(OptimizationReport report) {
        TaskSummary result = new TaskSummary();
        for (OptimizationReport.Issue issue : report.getIssues()) {
            if ("POLICY".equals(issue.getCategory())) result.setPolicyIssueCount(result.getPolicyIssueCount() + 1);
            else result.setPerformanceIssueCount(result.getPerformanceIssueCount() + 1);
        }
        result.setLimitationCount(report.getLimitations().size()); return result;
    }
    private void require(boolean condition) { if (!condition) throw new OptimizationFailure("REPORT_INVALID"); }
    private String text(JsonNode parent, String field) {
        require(parent != null && parent.isObject()); JsonNode value = parent.get(field);
        require(value != null && value.isTextual() && !value.textValue().trim().isEmpty()); return value.textValue();
    }
    private void array(JsonNode parent, String field) { require(parent.isObject() && parent.has(field) && parent.get(field).isArray()); }
    private void integer(JsonNode parent, String field, boolean nullable) {
        require(parent.isObject() && parent.has(field)); JsonNode value = parent.get(field);
        require((nullable && value.isNull()) || (value.isIntegralNumber() && value.canConvertToInt()));
    }
}
