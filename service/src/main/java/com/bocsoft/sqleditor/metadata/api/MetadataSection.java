package com.bocsoft.sqleditor.metadata.api;

import java.util.Collections;
import java.util.List;

public final class MetadataSection<T> {
    private final List<T> items;
    private final String coverage;
    private final Integer appliedLimit;

    public MetadataSection(List<T> items, String coverage, Integer appliedLimit) {
        this.items = items == null ? Collections.<T>emptyList() : items;
        this.coverage = coverage;
        this.appliedLimit = appliedLimit;
    }

    public static <T> MetadataSection<T> unavailable() {
        return new MetadataSection<T>(Collections.<T>emptyList(), MetadataCoverage.UNAVAILABLE, null);
    }

    public static <T> MetadataSection<T> complete(List<T> items, int appliedLimit) {
        return new MetadataSection<T>(items, MetadataCoverage.COMPLETE, Integer.valueOf(appliedLimit));
    }

    public static <T> MetadataSection<T> truncated(List<T> items, int appliedLimit) {
        return new MetadataSection<T>(items, MetadataCoverage.TRUNCATED, Integer.valueOf(appliedLimit));
    }

    public List<T> getItems() { return items; }
    public String getCoverage() { return coverage; }
    public Integer getAppliedLimit() { return appliedLimit; }

    public boolean isUnavailable() { return MetadataCoverage.UNAVAILABLE.equals(coverage); }
    public boolean isTruncated() { return MetadataCoverage.TRUNCATED.equals(coverage); }
}
