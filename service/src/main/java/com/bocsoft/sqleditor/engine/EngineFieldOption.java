package com.bocsoft.sqleditor.engine;

public final class EngineFieldOption {
    private final String value;
    private final String label;
    public EngineFieldOption(String value, String label) {
        this.value = value;
        this.label = label;
    }
    public String getValue() { return value; }
    public String getLabel() { return label; }
}
