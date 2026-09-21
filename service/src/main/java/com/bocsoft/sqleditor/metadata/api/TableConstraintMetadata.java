package com.bocsoft.sqleditor.metadata.api;

public final class TableConstraintMetadata {
    private final MetadataSection<UniqueKeyItem> uniqueKeys;
    private final MetadataSection<ForeignKeyItem> foreignKeys;
    private final MetadataSection<IndexItem> indexes;

    public TableConstraintMetadata(MetadataSection<UniqueKeyItem> uniqueKeys,
                                   MetadataSection<ForeignKeyItem> foreignKeys,
                                   MetadataSection<IndexItem> indexes) {
        this.uniqueKeys = uniqueKeys;
        this.foreignKeys = foreignKeys;
        this.indexes = indexes;
    }

    public static TableConstraintMetadata unavailable() {
        return new TableConstraintMetadata(
            MetadataSection.<UniqueKeyItem>unavailable(),
            MetadataSection.<ForeignKeyItem>unavailable(),
            MetadataSection.<IndexItem>unavailable());
    }

    public MetadataSection<UniqueKeyItem> getUniqueKeys() { return uniqueKeys; }
    public MetadataSection<ForeignKeyItem> getForeignKeys() { return foreignKeys; }
    public MetadataSection<IndexItem> getIndexes() { return indexes; }
}
