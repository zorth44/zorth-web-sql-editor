package com.bocsoft.sqleditor.metadata.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class ConstraintColumns {
    private ConstraintColumns() { }

    public static List<String> copy(List<String> columns) {
        if (columns == null) return Collections.emptyList();
        return Collections.unmodifiableList(new ArrayList<String>(columns));
    }

    public static List<String> requireNonempty(List<String> columns, String label) {
        List<String> copy = copy(columns);
        if (copy.isEmpty()) throw new IllegalArgumentException(label + " must be nonempty");
        for (String column : copy) {
            if (column == null || column.trim().isEmpty()) {
                throw new IllegalArgumentException(label + " must not contain blank names");
            }
        }
        return copy;
    }

    public static List<String> requirePaired(List<String> source, List<String> target, String label) {
        List<String> left = requireNonempty(source, label);
        List<String> right = requireNonempty(target, label);
        if (left.size() != right.size()) {
            throw new IllegalArgumentException(label + " source and target must be equal in length");
        }
        return left;
    }

    public static boolean validPair(List<String> source, List<String> target) {
        return nonempty(source) && nonempty(target) && source.size() == target.size();
    }

    public static boolean nonempty(List<String> columns) {
        if (columns == null || columns.isEmpty()) return false;
        for (String column : columns) {
            if (column == null || column.trim().isEmpty()) return false;
        }
        return true;
    }

    public static String setKey(List<String> columns) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) out.append('\0');
            out.append(columns.get(i).toLowerCase(Locale.ROOT));
        }
        return out.toString();
    }
}
