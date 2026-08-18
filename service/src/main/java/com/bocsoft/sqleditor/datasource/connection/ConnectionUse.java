package com.bocsoft.sqleditor.datasource.connection;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.Executor;

public final class ConnectionUse {
    private static final Executor INLINE = new Executor() {
        @Override public void execute(Runnable command) { command.run(); }
    };

    private ConnectionUse() { }

    public interface Work<T> { T run(Connection connection) throws SQLException; }

    public static <T> T execute(Connection connection, String defaultCatalog, Work<T> work) throws SQLException {
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
                resetAndClose(connection, defaultCatalog);
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
                String current = connection.getCatalog();
                discard = hasText(current);
            } catch (SQLException e) { if (failure == null) failure = e; }
        }
        try {
            connection.clearWarnings();
        } catch (SQLException e) { if (failure == null) failure = e; }
        if (discard) {
            try {
                connection.abort(INLINE);
            } catch (SQLException e) { if (failure == null) failure = e; }
            catch (RuntimeException | Error ignored) { }
        }
        try {
            connection.close();
        } catch (SQLException e) { if (failure == null) failure = e; }
        if (failure != null) throw failure;
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
