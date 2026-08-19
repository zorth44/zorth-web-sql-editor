package com.bocsoft.sqleditor.engine;

import java.util.List;

public final class EngineCatalogResponse {
    private final List<EngineDescriptor> items;
    public EngineCatalogResponse(List<EngineDescriptor> items) { this.items = items; }
    public List<EngineDescriptor> getItems() { return items; }
}
