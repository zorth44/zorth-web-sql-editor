package com.bocsoft.sqleditor.datasource;

import java.time.Instant;

public final class CursorPosition {
    private final Instant updatedAt;
    private final String id;
    public CursorPosition(Instant updatedAt, String id) { this.updatedAt=updatedAt; this.id=id; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getId() { return id; }
}
