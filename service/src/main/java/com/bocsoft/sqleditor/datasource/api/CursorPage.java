package com.bocsoft.sqleditor.datasource.api;

import java.util.List;

public class CursorPage<T> {
    private final List<T> items;
    private final String nextPageToken;
    public CursorPage(List<T> items, String nextPageToken) { this.items=items; this.nextPageToken=nextPageToken; }
    public List<T> getItems() { return items; }
    public String getNextPageToken() { return nextPageToken; }
}
