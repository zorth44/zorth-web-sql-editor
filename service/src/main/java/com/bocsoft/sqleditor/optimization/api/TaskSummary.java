package com.bocsoft.sqleditor.optimization.api;

@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS)
public class TaskSummary {
    private int policyIssueCount;
    private int performanceIssueCount;
    private int limitationCount;
    public int getPolicyIssueCount() { return policyIssueCount; }
    public void setPolicyIssueCount(int value) { policyIssueCount = value; }
    public int getPerformanceIssueCount() { return performanceIssueCount; }
    public void setPerformanceIssueCount(int value) { performanceIssueCount = value; }
    public int getLimitationCount() { return limitationCount; }
    public void setLimitationCount(int value) { limitationCount = value; }
}
