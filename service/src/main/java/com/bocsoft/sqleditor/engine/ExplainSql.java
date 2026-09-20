package com.bocsoft.sqleditor.engine;

import java.util.Locale;

public final class ExplainSql {
    private ExplainSql() {}

    public static int skipTrivia(String sql, int i) {
        int n = sql.length();
        while (i < n) {
            char c = sql.charAt(i);
            if (Character.isWhitespace(c)) { i++; continue; }
            if (c == '#' ) { i = skipLine(sql, i + 1); continue; }
            if (c == '-' && i + 1 < n && sql.charAt(i + 1) == '-'
                && (i + 2 >= n || Character.isWhitespace(sql.charAt(i + 2)))) {
                i = skipLine(sql, i + 2);
                continue;
            }
            if (c == '/' && i + 1 < n && sql.charAt(i + 1) == '*') {
                int end = sql.indexOf("*/", i + 2);
                i = end < 0 ? n : end + 2;
                continue;
            }
            break;
        }
        return i;
    }

    public static String firstKeyword(String sql) {
        int i = skipTrivia(sql, 0);
        return readIdent(sql, i);
    }

    public static String innerAfterExplain(String sql, boolean postgres) {
        int i = skipTrivia(sql, 0);
        String first = readIdent(sql, i);
        if (!"EXPLAIN".equalsIgnoreCase(first)) return sql.trim();
        i += first.length();
        i = skipTrivia(sql, i);
        if (postgres && i < sql.length() && sql.charAt(i) == '(') {
            i = skipParen(sql, i);
            i = skipTrivia(sql, i);
            return sql.substring(i).trim();
        }
        while (i < sql.length()) {
            i = skipTrivia(sql, i);
            if (i >= sql.length()) break;
            if (postgres && sql.charAt(i) == '(') {
                i = skipParen(sql, i);
                continue;
            }
            String ident = readIdent(sql, i);
            if (ident.isEmpty()) break;
            if (isInnerStart(ident)) return sql.substring(i).trim();
            if ("ANALYZE".equalsIgnoreCase(ident) || "FORMAT".equalsIgnoreCase(ident)
                || "VERBOSE".equalsIgnoreCase(ident) || "BUFFERS".equalsIgnoreCase(ident)
                || "WAL".equalsIgnoreCase(ident) || "SETTINGS".equalsIgnoreCase(ident)
                || "JSON".equalsIgnoreCase(ident) || "TREE".equalsIgnoreCase(ident)
                || "TRADITIONAL".equalsIgnoreCase(ident)) {
                i += ident.length();
                i = skipTrivia(sql, i);
                if (i < sql.length() && sql.charAt(i) == '=') {
                    i = skipTrivia(sql, i + 1);
                    String value = readIdent(sql, i);
                    if (!value.isEmpty()) i += value.length();
                }
                continue;
            }
            break;
        }
        return sql.substring(i).trim();
    }

    public static boolean optionsContainAnalyze(String sql, boolean postgres) {
        int i = skipTrivia(sql, 0);
        String first = readIdent(sql, i);
        if (!"EXPLAIN".equalsIgnoreCase(first)) return false;
        i += first.length();
        i = skipTrivia(sql, i);
        if (i < sql.length() && sql.charAt(i) == '(') {
            String inside = sql.substring(i + 1, skipParen(sql, i) - 1);
            return containsBareAnalyze(inside);
        }
        while (i < sql.length()) {
            i = skipTrivia(sql, i);
            if (i >= sql.length()) return false;
            String ident = readIdent(sql, i);
            if (ident.isEmpty()) return false;
            if ("ANALYZE".equalsIgnoreCase(ident)) return true;
            if (isInnerStart(ident)) return false;
            if ("FORMAT".equalsIgnoreCase(ident) || "VERBOSE".equalsIgnoreCase(ident)
                || "JSON".equalsIgnoreCase(ident) || "TREE".equalsIgnoreCase(ident)
                || "TRADITIONAL".equalsIgnoreCase(ident) || "BUFFERS".equalsIgnoreCase(ident)) {
                i += ident.length();
                i = skipTrivia(sql, i);
                if (i < sql.length() && sql.charAt(i) == '=') {
                    i = skipTrivia(sql, i + 1);
                    String value = readIdent(sql, i);
                    if (!value.isEmpty()) i += value.length();
                }
                continue;
            }
            return false;
        }
        return false;
    }

    private static boolean containsBareAnalyze(String options) {
        int i = 0;
        while (i < options.length()) {
            i = skipTrivia(options, i);
            if (i >= options.length()) return false;
            String ident = readIdent(options, i);
            if (ident.isEmpty()) { i++; continue; }
            if ("ANALYZE".equalsIgnoreCase(ident)) return true;
            i += ident.length();
            i = skipTrivia(options, i);
            if (i < options.length() && options.charAt(i) == '=') {
                i = skipTrivia(options, i + 1);
                String value = readIdent(options, i);
                if (!value.isEmpty()) i += value.length();
            }
            i = skipTrivia(options, i);
            if (i < options.length() && options.charAt(i) == ',') i++;
        }
        return false;
    }

    private static boolean isInnerStart(String ident) {
        String w = ident.toUpperCase(Locale.ROOT);
        return "SELECT".equals(w) || "WITH".equals(w) || "INSERT".equals(w) || "UPDATE".equals(w)
            || "DELETE".equals(w) || "REPLACE".equals(w) || "CREATE".equals(w) || "ALTER".equals(w)
            || "DROP".equals(w) || "TRUNCATE".equals(w) || "SHOW".equals(w) || "DESC".equals(w)
            || "DESCRIBE".equals(w) || "VALUES".equals(w) || "TABLE".equals(w);
    }

    private static int skipLine(String sql, int i) {
        while (i < sql.length() && sql.charAt(i) != '\n' && sql.charAt(i) != '\r') i++;
        return i;
    }

    private static int skipParen(String sql, int i) {
        int depth = 0;
        for (; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') {
                depth--;
                if (depth == 0) return i + 1;
            }
        }
        return sql.length();
    }

    private static String readIdent(String sql, int i) {
        if (i >= sql.length() || !Character.isLetter(sql.charAt(i))) return "";
        int j = i + 1;
        while (j < sql.length() && (Character.isLetterOrDigit(sql.charAt(j)) || sql.charAt(j) == '_')) j++;
        return sql.substring(i, j);
    }
}
