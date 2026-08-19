package com.bocsoft.sqleditor.engine;

public final class EngineCapabilities {
    private final boolean defaultNamespaceRequired;
    private final boolean canSwitchNamespaceOnConnection;
    public EngineCapabilities(boolean defaultNamespaceRequired, boolean canSwitchNamespaceOnConnection) {
        this.defaultNamespaceRequired = defaultNamespaceRequired;
        this.canSwitchNamespaceOnConnection = canSwitchNamespaceOnConnection;
    }
    public boolean isDefaultNamespaceRequired() { return defaultNamespaceRequired; }
    public boolean isCanSwitchNamespaceOnConnection() { return canSwitchNamespaceOnConnection; }
}
