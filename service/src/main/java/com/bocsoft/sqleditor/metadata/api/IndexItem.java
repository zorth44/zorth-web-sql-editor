package com.bocsoft.sqleditor.metadata.api;
import java.util.List;
public class IndexItem {private final String name,type;private final boolean unique;private final List<String> columns;public IndexItem(String name,boolean unique,String type,List<String> columns){this.name=name;this.unique=unique;this.type=type;this.columns=columns;}public String getName(){return name;}public boolean isUnique(){return unique;}public String getType(){return type;}public List<String> getColumns(){return columns;}}
