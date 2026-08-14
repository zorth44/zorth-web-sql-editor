package com.bocsoft.sqleditor.datasource;

import com.bocsoft.sqleditor.datasource.connection.ConnectionConfiguration;

public final class SavedDataSource {
    private final String id; private final String name; private final long version;
    private final String defaultDatabase; private final ConnectionConfiguration configuration;
    public SavedDataSource(String id,String name,long version,String defaultDatabase,ConnectionConfiguration configuration){
        this.id=id;this.name=name;this.version=version;this.defaultDatabase=defaultDatabase;this.configuration=configuration;
    }
    public String getId(){return id;} public String getName(){return name;} public long getVersion(){return version;}
    public String getDefaultDatabase(){return defaultDatabase;} public ConnectionConfiguration getConfiguration(){return configuration;}
}
