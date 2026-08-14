package com.bocsoft.sqleditor.metadata.api;
import java.util.List;
public class PrimaryKeyItem {private final String name;private final List<String> columns;public PrimaryKeyItem(String name,List<String> columns){this.name=name;this.columns=columns;}public String getName(){return name;}public List<String> getColumns(){return columns;}}
