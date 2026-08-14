package com.bocsoft.sqleditor.datasource;

import com.bocsoft.sqleditor.auth.AuthContext;
import com.bocsoft.sqleditor.datasource.connection.DynamicPoolManager;
import java.sql.Connection;
import java.sql.SQLException;
import org.springframework.stereotype.Component;

@Component
public class TargetConnectionProvider {
    private final DataSourceService dataSources; private final DynamicPoolManager pools;
    public TargetConnectionProvider(DataSourceService dataSources,DynamicPoolManager pools){this.dataSources=dataSources;this.pools=pools;}
    public SavedDataSource require(AuthContext auth,String id){return dataSources.requireSaved(auth,id);}
    public Connection borrow(SavedDataSource source)throws SQLException{return pools.borrow(source.getId(),source.getVersion(),source.getConfiguration());}
}
