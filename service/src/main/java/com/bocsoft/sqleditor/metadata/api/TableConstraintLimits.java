package com.bocsoft.sqleditor.metadata.api;

public final class TableConstraintLimits {
    private final int maxUniqueKeys;
    private final int maxForeignKeys;
    private final int maxIndexes;

    public TableConstraintLimits(int maxUniqueKeys, int maxForeignKeys, int maxIndexes) {
        this.maxUniqueKeys = maxUniqueKeys;
        this.maxForeignKeys = maxForeignKeys;
        this.maxIndexes = maxIndexes;
    }

    public int getMaxUniqueKeys() { return maxUniqueKeys; }
    public int getMaxForeignKeys() { return maxForeignKeys; }
    public int getMaxIndexes() { return maxIndexes; }
}
