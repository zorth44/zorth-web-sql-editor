package com.bocsoft.sqleditor.optimization.api;

@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS)
public class OptimizationReport {
    private String verdict;
    private java.util.List<Issue> issues;
    private java.util.List<Suggestion> suggestions;
    private java.util.List<Limitation> limitations;
    public String getVerdict() { return verdict; }
    public void setVerdict(String value) { verdict = value; }
    public java.util.List<Issue> getIssues() { return issues; }
    public void setIssues(java.util.List<Issue> value) { issues = value; }
    public java.util.List<Suggestion> getSuggestions() { return suggestions; }
    public void setSuggestions(java.util.List<Suggestion> value) { suggestions = value; }
    public java.util.List<Limitation> getLimitations() { return limitations; }
    public void setLimitations(java.util.List<Limitation> value) { limitations = value; }
    public static class Issue {
    private String id;
    private Integer ruleId;
    private String category;
    private String priority;
    private String severity;
    private java.util.List<Evidence> evidence;
    private String detail;
    public String getId() { return id; }
    public void setId(String value) { id = value; }
    public Integer getRuleId() { return ruleId; }
    public void setRuleId(Integer value) { ruleId = value; }
    public String getCategory() { return category; }
    public void setCategory(String value) { category = value; }
    public String getPriority() { return priority; }
    public void setPriority(String value) { priority = value; }
    public String getSeverity() { return severity; }
    public void setSeverity(String value) { severity = value; }
    public java.util.List<Evidence> getEvidence() { return evidence; }
    public void setEvidence(java.util.List<Evidence> value) { evidence = value; }
    public String getDetail() { return detail; }
    public void setDetail(String value) { detail = value; }
    }
    public static class Evidence {
    private String kind;
    private String content;
    public String getKind() { return kind; }
    public void setKind(String value) { kind = value; }
    public String getContent() { return content; }
    public void setContent(String value) { content = value; }
    }
    public static class Suggestion {
    private String id;
    private java.util.List<String> issueIds;
    private String detail;
    public String getId() { return id; }
    public void setId(String value) { id = value; }
    public java.util.List<String> getIssueIds() { return issueIds; }
    public void setIssueIds(java.util.List<String> value) { issueIds = value; }
    public String getDetail() { return detail; }
    public void setDetail(String value) { detail = value; }
    }
    public static class Limitation {
    private Integer ruleId;
    private String reason;
    public Integer getRuleId() { return ruleId; }
    public void setRuleId(Integer value) { ruleId = value; }
    public String getReason() { return reason; }
    public void setReason(String value) { reason = value; }
    }
}
