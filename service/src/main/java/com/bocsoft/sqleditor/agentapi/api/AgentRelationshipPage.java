package com.bocsoft.sqleditor.agentapi.api;

import com.bocsoft.sqleditor.metadata.api.RelationshipEdge;
import java.util.List;

public class AgentRelationshipPage {
    private final List<RelationshipEdge> items;
    private final String nextPageToken;
    private final String coverage;
    private final Integer appliedLimit;

    public AgentRelationshipPage(List<RelationshipEdge> items, String nextPageToken, String coverage, Integer appliedLimit) {
        this.items = items;
        this.nextPageToken = nextPageToken;
        this.coverage = coverage;
        this.appliedLimit = appliedLimit;
    }

    public List<RelationshipEdge> getItems() { return items; }
    public String getNextPageToken() { return nextPageToken; }
    public String getCoverage() { return coverage; }
    public Integer getAppliedLimit() { return appliedLimit; }
}
