package com.bocsoft.sqleditor.datasource.connection;

import java.sql.Connection;
import java.sql.SQLException;

public final class ConnectionUse {
    private ConnectionUse(){ }
    public interface Work<T>{T run(Connection connection)throws SQLException;}
    public static <T>T execute(Connection connection,String defaultCatalog,Work<T> work)throws SQLException{
        try{return work.run(connection);}finally{resetAndClose(connection,defaultCatalog);}
    }
    public static void resetAndClose(Connection connection,String defaultCatalog)throws SQLException{
        SQLException failure=null;
        try{if(!connection.getAutoCommit())connection.rollback();}catch(SQLException e){failure=e;}
        try{connection.setAutoCommit(true);}catch(SQLException e){if(failure==null)failure=e;}
        try{connection.setCatalog(defaultCatalog);}catch(SQLException e){if(failure==null)failure=e;}
        try{connection.clearWarnings();}catch(SQLException e){if(failure==null)failure=e;}
        try{connection.close();}catch(SQLException e){if(failure==null)failure=e;}
        if(failure!=null)throw failure;
    }
}
