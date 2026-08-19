package com.bocsoft.sqleditor.datasource.connection;

import com.bocsoft.sqleditor.engine.EngineSupport;
import java.sql.Connection;
import java.sql.SQLException;

public final class ConnectionUse {
    private ConnectionUse() { }

    public interface Work<T> { T run(Connection connection) throws SQLException; }

    public interface Evict { void evict(Connection connection); }

    public static <T> T execute(Connection connection, String defaultCatalog, EngineSupport engine, Work<T> work) throws SQLException {
        return execute(connection, defaultCatalog, engine, null, work);
    }

    public static <T> T execute(Connection connection, String defaultCatalog, EngineSupport engine, Evict evict, Work<T> work) throws SQLException {
        T result = null;
        SQLException workFailure = null;
        RuntimeException runtimeFailure = null;
        try {
            result = work.run(connection);
        } catch (SQLException e) {
            workFailure = e;
        } catch (RuntimeException e) {
            runtimeFailure = e;
        } finally {
            try {
                resetAndClose(connection, defaultCatalog, engine, evict);
            } catch (SQLException resetFailure) {
                if (workFailure != null) workFailure.setNextException(resetFailure);
                else if (runtimeFailure != null) runtimeFailure.addSuppressed(resetFailure);
            }
        }
        if (runtimeFailure != null) throw runtimeFailure;
        if (workFailure != null) throw workFailure;
        return result;
    }

    public static void resetAndClose(Connection connection, String defaultCatalog, EngineSupport engine) throws SQLException {
        resetAndClose(connection, defaultCatalog, engine, null);
    }

    public static void resetAndClose(Connection connection, String defaultCatalog, EngineSupport engine, Evict evict) throws SQLException {
        SQLException failure = null;
        try {
            if (!connection.getAutoCommit()) connection.rollback();
        } catch (SQLException e) { failure = e; }
        try {
            connection.setAutoCommit(true);
        } catch (SQLException e) { if (failure == null) failure = e; }
        boolean discard = false;
        try {
            discard = engine.restoreSession(connection, defaultCatalog);
        } catch (SQLException e) { if (failure == null) failure = e; }
        if (!discard) {
            try {
                connection.clearWarnings();
            } catch (SQLException e) { if (failure == null) failure = e; }
        }
        try {
            if (discard && evict != null) evict.evict(connection);
            else connection.close();
        } catch (SQLException e) { if (failure == null) failure = e; }
        catch (RuntimeException ignored) {
            try {
                if (!connection.isClosed()) connection.close();
            } catch (SQLException e) { if (failure == null) failure = e; }
        }
        if (failure != null) throw failure;
    }
}
