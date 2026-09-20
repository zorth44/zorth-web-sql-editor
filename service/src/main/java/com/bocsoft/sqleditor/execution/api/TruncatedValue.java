package com.bocsoft.sqleditor.execution.api;

public class TruncatedValue {
    private final boolean truncated = true;
    private final long byteLength;

    public TruncatedValue(long byteLength) {
        this.byteLength = byteLength;
    }

    public boolean isTruncated() { return truncated; }
    public long getByteLength() { return byteLength; }
}
