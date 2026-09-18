package com.bocsoft.sqleditor.engine;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SqlLead {
    private static final Pattern TRIVIA = Pattern.compile(
        "(?is)^(?:\\s|/\\*.*?\\*/|--[^\\r\\n]*(?:\\r?\\n|$)|#[^\\r\\n]*(?:\\r?\\n|$))*");
    private static final Pattern WORD = Pattern.compile("(?is)^([A-Za-z]+)");

    private SqlLead() {}

    public static String skipTrivia(String sql) {
        if (sql == null) return "";
        Matcher matcher = TRIVIA.matcher(sql);
        return matcher.find() ? sql.substring(matcher.end()) : sql;
    }

    public static String firstWord(String sql) {
        String rest = skipTrivia(sql);
        Matcher matcher = WORD.matcher(rest);
        return matcher.find() ? matcher.group(1).toUpperCase(Locale.ROOT) : "";
    }

    public static String afterFirstWord(String sql) {
        String rest = skipTrivia(sql);
        Matcher matcher = WORD.matcher(rest);
        if (!matcher.find()) return "";
        return skipTrivia(rest.substring(matcher.end()));
    }

    public static int matchingParen(String sql) {
        if (sql == null || sql.isEmpty() || sql.charAt(0) != '(') return -1;
        int depth = 0;
        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }
}
