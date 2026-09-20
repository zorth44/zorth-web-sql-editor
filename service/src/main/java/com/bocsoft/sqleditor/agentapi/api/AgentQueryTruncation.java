package com.bocsoft.sqleditor.agentapi.api;

public class AgentQueryTruncation {
    private final boolean rowLimitReached;
    private final boolean resultBytesReached;
    private final int cellTruncatedCount;

    public AgentQueryTruncation(boolean rowLimitReached, boolean resultBytesReached, int cellTruncatedCount) {
        this.rowLimitReached = rowLimitReached;
        this.resultBytesReached = resultBytesReached;
        this.cellTruncatedCount = cellTruncatedCount;
    }

    public boolean isRowLimitReached() { return rowLimitReached; }
    public boolean isResultBytesReached() { return resultBytesReached; }
    public int getCellTruncatedCount() { return cellTruncatedCount; }
}
