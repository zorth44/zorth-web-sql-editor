package com.bocsoft.sqleditor.agentapi.api;

public class AgentQueryMasking {
    private final boolean applied;
    private final String policyId;

    public AgentQueryMasking(boolean applied, String policyId) {
        this.applied = applied;
        this.policyId = policyId;
    }

    public boolean isApplied() { return applied; }
    public String getPolicyId() { return policyId; }
}
