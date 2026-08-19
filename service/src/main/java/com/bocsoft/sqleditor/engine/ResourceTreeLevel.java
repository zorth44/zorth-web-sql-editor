package com.bocsoft.sqleditor.engine;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public final class ResourceTreeLevel {
    private final String kind;
    private final String label;
    private final String filterLabel;
    private final String listEndpoint;
    private final String parentKind;

    public ResourceTreeLevel(String kind, String label, String filterLabel, String listEndpoint, String parentKind) {
        this.kind = kind;
        this.label = label;
        this.filterLabel = filterLabel;
        this.listEndpoint = listEndpoint;
        this.parentKind = parentKind;
    }

    public static ResourceTreeLevel namespace(String label, String filterLabel, String listEndpoint) {
        return new ResourceTreeLevel("NAMESPACE", label, filterLabel, listEndpoint, null);
    }
    public static ResourceTreeLevel child(String kind, String label, String filterLabel, String parentKind) {
        return new ResourceTreeLevel(kind, label, filterLabel, null, parentKind);
    }

    public String getKind() { return kind; }
    public String getLabel() { return label; }
    public String getFilterLabel() { return filterLabel; }
    public String getListEndpoint() { return listEndpoint; }
    public String getParentKind() { return parentKind; }
}
