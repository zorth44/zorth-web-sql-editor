package com.bocsoft.sqleditor.metadata.api;
public class TableItem { private final String database; private final String name; private final String type; private final String comment;
    public TableItem(String database,String name,String type,String comment){this.database=database;this.name=name;this.type=type;this.comment=comment;}
    public String getDatabase(){return database;} public String getName(){return name;} public String getType(){return type;} public String getComment(){return comment;}}
