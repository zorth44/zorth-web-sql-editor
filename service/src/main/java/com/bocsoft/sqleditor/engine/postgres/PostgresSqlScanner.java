package com.bocsoft.sqleditor.engine.postgres;

import com.bocsoft.sqleditor.common.ApiException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;

final class PostgresSqlScanner {
    String requireSingle(String sql) {
        List<String> statements = split(sql);
        if (statements.isEmpty()) throw ApiException.validation("statement", "REQUIRED", "SQL 不能为空");
        if (statements.size() != 1) throw new ApiException(HttpStatus.BAD_REQUEST, "MULTI_STATEMENT_NOT_SUPPORTED", "暂不支持批量执行");
        return statements.get(0);
    }

    List<String> split(String sql) {
        List<String> out = new ArrayList<String>();
        if (sql == null) return out;
        int start = 0;
        State state = State.NORMAL;
        String dollarTag = null;
        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i), n = i + 1 < sql.length() ? sql.charAt(i + 1) : '\0';
            switch (state) {
                case NORMAL:
                    if (isEscapedStringStart(sql, i)) { state = State.ESC_STRING; i++; }
                    else if (c == '\'') state = State.SINGLE;
                    else if (c == '"') state = State.DOUBLE;
                    else if (c == '$') {
                        int tagEnd = dollarTagEnd(sql, i);
                        if (tagEnd >= 0) {
                            dollarTag = sql.substring(i, tagEnd + 1);
                            state = State.DOLLAR;
                            i = tagEnd;
                        }
                    } else if (c == '-' && n == '-' && (i + 2 >= sql.length() || Character.isWhitespace(sql.charAt(i + 2)))) {
                        state = State.LINE; i++;
                    } else if (c == '/' && n == '*') { state = State.BLOCK; i++; }
                    else if (c == ';') { add(out, sql.substring(start, i)); start = i + 1; }
                    break;
                case SINGLE:
                    if (c == '\'' && n == '\'') i++;
                    else if (c == '\'') state = State.NORMAL;
                    break;
                case ESC_STRING:
                    if (c == '\\') i++;
                    else if (c == '\'' && n == '\'') i++;
                    else if (c == '\'') state = State.NORMAL;
                    break;
                case DOUBLE:
                    if (c == '"' && n == '"') i++;
                    else if (c == '"') state = State.NORMAL;
                    break;
                case DOLLAR:
                    if (dollarTag != null && startsWithAt(sql, i, dollarTag)) {
                        i += dollarTag.length() - 1;
                        dollarTag = null;
                        state = State.NORMAL;
                    }
                    break;
                case LINE:
                    if (c == '\n' || c == '\r') state = State.NORMAL;
                    break;
                case BLOCK:
                    if (c == '*' && n == '/') { state = State.NORMAL; i++; }
                    break;
                default:
                    break;
            }
        }
        if (state == State.SINGLE || state == State.ESC_STRING || state == State.DOUBLE || state == State.DOLLAR || state == State.BLOCK) {
            throw ApiException.validation("statement", "INVALID", "SQL 引号或注释未闭合");
        }
        add(out, sql.substring(start));
        return out;
    }

    private boolean isEscapedStringStart(String sql, int i) {
        char c = sql.charAt(i);
        if ((c != 'E' && c != 'e') || i + 1 >= sql.length() || sql.charAt(i + 1) != '\'') return false;
        return i == 0 || !isIdent(sql.charAt(i - 1));
    }

    private int dollarTagEnd(String sql, int i) {
        if (i >= sql.length() || sql.charAt(i) != '$') return -1;
        int j = i + 1;
        if (j < sql.length() && sql.charAt(j) == '$') return j;
        if (j >= sql.length() || !isTagStart(sql.charAt(j))) return -1;
        j++;
        while (j < sql.length() && isTagPart(sql.charAt(j))) j++;
        if (j < sql.length() && sql.charAt(j) == '$') return j;
        return -1;
    }

    private boolean startsWithAt(String sql, int i, String tag) {
        return i + tag.length() <= sql.length() && sql.regionMatches(i, tag, 0, tag.length());
    }

    private boolean isTagStart(char c) { return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || c == '_'; }
    private boolean isTagPart(char c) { return isTagStart(c) || (c >= '0' && c <= '9'); }
    private boolean isIdent(char c) { return isTagPart(c); }

    private void add(List<String> out, String v) {
        String clean = v.trim();
        if (!clean.isEmpty()) out.add(clean);
    }

    private enum State { NORMAL, SINGLE, ESC_STRING, DOUBLE, DOLLAR, LINE, BLOCK }
}
