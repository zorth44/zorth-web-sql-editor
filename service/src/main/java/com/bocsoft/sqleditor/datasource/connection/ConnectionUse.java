package com.bocsoft.sqleditor.datasource.connection;

import java.sql.Connection;
import java.sql.SQLException;

public final class ConnectionUse {
    private ConnectionUse() { }

    public interface Work<T> { T run(Connection connection) throws SQLException; }

    public interface Evict { void evict(Connection connection); }

    public static <T> T execute(Connection connection, String defaultCatalog, Work<T> work) throws SQLException {
        return execute(connection, defaultCatalog, null, work);
    }

    public static <T> T execute(Connection connection, String defaultCatalog, Evict evict, Work<T> work) throws SQLException {
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
                resetAndClose(connection, defaultCatalog, evict);
            } catch (SQLException resetFailure) {
                if (workFailure != null) workFailure.setNextException(resetFailure);
                else if (runtimeFailure != null) runtimeFailure.addSuppressed(resetFailure);
            }
        }
        if (runtimeFailure != null) throw runtimeFailure;
        if (workFailure != null) throw workFailure;
        return result;
    }

    public static void resetAndClose(Connection connection, String defaultCatalog) throws SQLException {
        resetAndClose(connection, defaultCatalog, null);
    }

    public static void resetAndClose(Connection connection, String defaultCatalog, Evict evict) throws SQLException {
        SQLException failure = null;
        try {
            if (!connection.getAutoCommit()) connection.rollback();
        } catch (SQLException e) { failure = e; }
        try {
            connection.setAutoCommit(true);
        } catch (SQLException e) { if (failure == null) failure = e; }
        boolean discard = false;
        if (hasText(defaultCatalog)) {
            try {
                connection.setCatalog(defaultCatalog);
            } catch (SQLException e) { if (failure == null) failure = e; }
        } else {
            try {
                discard = hasText(connection.getCatalog());
            } catch (SQLException e) { if (failure == null) failure = e; }
        }
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

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
