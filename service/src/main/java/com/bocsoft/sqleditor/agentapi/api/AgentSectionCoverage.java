package com.bocsoft.sqleditor.agentapi.api;

import com.fasterxml.jackson.annotation.JsonInclude;

public class AgentSectionCoverage {
    private final String status;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final Integer appliedLimit;

    public AgentSectionCoverage(String status, Integer appliedLimit) {
        this.status = status;
        this.appliedLimit = appliedLimit;
    }

    public String getStatus() { return status; }
    public Integer getAppliedLimit() { return appliedLimit; }
}
