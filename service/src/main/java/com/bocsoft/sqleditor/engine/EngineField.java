package com.bocsoft.sqleditor.engine;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public final class EngineField {
    private final String name;
    private final String kind;
    private final String widget;
    private final String label;
    private final boolean required;
    private final Boolean requiredOnCreate;
    private final Integer min;
    private final Integer max;
    private final Integer maxLength;
    private final String defaultValue;
    private final List<EngineFieldOption> options;

    public EngineField(String name, String kind, String widget, String label, boolean required,
                       Boolean requiredOnCreate, Integer min, Integer max, Integer maxLength,
                       String defaultValue, List<EngineFieldOption> options) {
        this.name = name;
        this.kind = kind;
        this.widget = widget;
        this.label = label;
        this.required = required;
        this.requiredOnCreate = requiredOnCreate;
        this.min = min;
        this.max = max;
        this.maxLength = maxLength;
        this.defaultValue = defaultValue;
        this.options = options == null ? null : Collections.unmodifiableList(new ArrayList<EngineFieldOption>(options));
    }

    public static EngineField connection(String name, String kind, String widget, String label, boolean required,
                                         Integer min, Integer max, Integer maxLength, String defaultValue,
                                         List<EngineFieldOption> options) {
        return new EngineField(name, kind, widget, label, required, null, min, max, maxLength, defaultValue, options);
    }

    public static EngineField password(String name, String label, int maxLength) {
        return new EngineField(name, "PASSWORD", "PASSWORD", label, false, Boolean.TRUE, null, null, Integer.valueOf(maxLength), null, null);
    }

    public static EngineField property(String name, String widget, String label, String defaultValue, List<String> optionValues) {
        return new EngineField(name, null, widget, label, false, null, null, null, null, defaultValue, options(optionValues));
    }

    public static List<EngineFieldOption> options(String... values) {
        if (values == null) return null;
        List<EngineFieldOption> items = new ArrayList<EngineFieldOption>();
        for (int i = 0; i < values.length; i++) items.add(new EngineFieldOption(values[i], values[i]));
        return items;
    }

    public static List<EngineFieldOption> labeled(String... valueLabelPairs) {
        List<EngineFieldOption> items = new ArrayList<EngineFieldOption>();
        for (int i = 0; i + 1 < valueLabelPairs.length; i += 2) {
            items.add(new EngineFieldOption(valueLabelPairs[i], valueLabelPairs[i + 1]));
        }
        return items;
    }

    private static List<EngineFieldOption> options(List<String> values) {
        if (values == null) return null;
        List<EngineFieldOption> items = new ArrayList<EngineFieldOption>();
        for (String value : values) items.add(new EngineFieldOption(value, value));
        return items;
    }

    public String getName() { return name; }
    public String getKind() { return kind; }
    public String getWidget() { return widget; }
    public String getLabel() { return label; }
    public boolean isRequired() { return required; }
    public Boolean getRequiredOnCreate() { return requiredOnCreate; }
    public Integer getMin() { return min; }
    public Integer getMax() { return max; }
    public Integer getMaxLength() { return maxLength; }
    public String getDefaultValue() { return defaultValue; }
    public List<EngineFieldOption> getOptions() { return options; }
}
