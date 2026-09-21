package com.bocsoft.sqleditor.metadata.api;

import java.util.List;

public final class RelationshipPage {
    private final List<RelationshipEdge> items;
    private final String nextPageToken;
    private final String coverage;
    private final Integer appliedLimit;

    public RelationshipPage(List<RelationshipEdge> items, String nextPageToken, String coverage, Integer appliedLimit) {
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
