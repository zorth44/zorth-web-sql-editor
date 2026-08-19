package com.bocsoft.sqleditor.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class EngineDescriptor {
    private final String id;
    private final String displayName;
    private final String family;
    private final int defaultPort;
    private final String editorLanguage;
    private final EngineCapabilities capabilities;
    private final List<EngineField> connectionFields;
    private final List<EngineField> propertyFields;
    private final List<ResourceTreeLevel> resourceTree;

    public EngineDescriptor(String id, String displayName, String family, int defaultPort, String editorLanguage,
                            EngineCapabilities capabilities, List<EngineField> connectionFields,
                            List<EngineField> propertyFields, List<ResourceTreeLevel> resourceTree) {
        this.id = id;
        this.displayName = displayName;
        this.family = family;
        this.defaultPort = defaultPort;
        this.editorLanguage = editorLanguage;
        this.capabilities = capabilities;
        this.connectionFields = Collections.unmodifiableList(new ArrayList<EngineField>(connectionFields));
        this.propertyFields = Collections.unmodifiableList(new ArrayList<EngineField>(propertyFields));
        this.resourceTree = Collections.unmodifiableList(new ArrayList<ResourceTreeLevel>(resourceTree));
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public String getFamily() { return family; }
    public int getDefaultPort() { return defaultPort; }
    public String getEditorLanguage() { return editorLanguage; }
    public EngineCapabilities getCapabilities() { return capabilities; }
    public List<EngineField> getConnectionFields() { return connectionFields; }
    public List<EngineField> getPropertyFields() { return propertyFields; }
    public List<ResourceTreeLevel> getResourceTree() { return resourceTree; }
}
