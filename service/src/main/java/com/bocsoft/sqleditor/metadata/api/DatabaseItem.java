package com.bocsoft.sqleditor.metadata.api;
public class DatabaseItem {
    private final String name;
    private final String kind;
    public DatabaseItem(String name){ this(name, "NAMESPACE"); }
    public DatabaseItem(String name, String kind){ this.name=name; this.kind=kind; }
    public String getName(){ return name; }
    public String getKind(){ return kind; }
}
