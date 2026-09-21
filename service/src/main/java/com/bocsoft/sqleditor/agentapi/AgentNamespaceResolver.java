package com.bocsoft.sqleditor.agentapi;

import com.bocsoft.sqleditor.common.ApiException;

final class AgentNamespaceResolver {

    private AgentNamespaceResolver() {}

    /**
     * Resolves optional database/schema aliases into the single NAMESPACE selector used by Web SQL.
     * Blank values are treated as absent. Unequal non-blank pairs are rejected.
     */
    static String resolve(String database, String schema) {
        String db = blankToNull(database);
        String sch = blankToNull(schema);
        if (db == null && sch == null) {
            return null;
        }
        if (db == null) {
            return sch;
        }
        if (sch == null) {
            return db;
        }
        if (db.equals(sch)) {
            return db;
        }
        throw ApiException.validation("schema", "CONFLICTING_NAMESPACE",
            "database 与 schema 必须表示同一 NAMESPACE，或只填写其中一个");
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
