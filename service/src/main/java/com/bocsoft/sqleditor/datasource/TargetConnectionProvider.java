package com.bocsoft.sqleditor.datasource;

import com.bocsoft.sqleditor.auth.AuthContext;
import com.bocsoft.sqleditor.datasource.connection.ConnectionUse;
import com.bocsoft.sqleditor.datasource.connection.DynamicPoolManager;
import com.bocsoft.sqleditor.engine.EngineRegistry;
import com.bocsoft.sqleditor.engine.EngineSupport;
import java.sql.Connection;
import java.sql.SQLException;
import org.springframework.stereotype.Component;

@Component
public class TargetConnectionProvider {
    private final DataSourceService dataSources; private final DynamicPoolManager pools; private final EngineRegistry engines;
    public TargetConnectionProvider(DataSourceService dataSources,DynamicPoolManager pools,EngineRegistry engines){
        this.dataSources=dataSources;this.pools=pools;this.engines=engines;
    }
    public SavedDataSource require(AuthContext auth,String id){return dataSources.requireSaved(auth,id);}
    public Connection borrow(SavedDataSource source)throws SQLException{return pools.borrow(source.getId(),source.getVersion(),source.getConfiguration());}
    public ConnectionUse.Evict evictor(SavedDataSource source){return connection -> pools.evict(source.getId(), connection);}
    public EngineSupport engine(SavedDataSource source){return engines.requireSaved(source.getEngine());}
    public void release(SavedDataSource source, Connection connection) throws SQLException {
        ConnectionUse.resetAndClose(connection, source.getDefaultDatabase(), engine(source), evictor(source));
    }
}
