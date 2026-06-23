package io.smithy.beam.ir.erlang;

import java.util.ArrayList;
import java.util.List;

final class ErlLayout {
    static final String INDENT_STEP = "    ";
    static final int SPEC_LINE_LIMIT = 100;

    private ErlLayout() {}

    static String indent(int depth) {
        return INDENT_STEP.repeat(Math.max(0, depth));
    }

    static List<String> indentLines(List<String> lines, int depth) {
        if (depth == 0) {
            return lines;
        }
        String prefix = indent(depth);
        List<String> out = new ArrayList<>(lines.size());
        for (String line : lines) {
            out.add(prefix + line);
        }
        return out;
    }

    static String renderAtom(String value) {
        if (needsQuotedAtom(value)) {
            return "'" + escapeSingleQuoted(value) + "'";
        }
        return value;
    }

    static String renderString(String value) {
        return "\"" + escapeDoubleQuoted(value) + "\"";
    }

    static String renderBinaryLiteral(String value) {
        return "<<\"" + escapeDoubleQuoted(value) + "\">>";
    }

    private static boolean needsQuotedAtom(String value) {
        if (value.isEmpty()) {
            return true;
        }
        char first = value.charAt(0);
        if (Character.isLowerCase(first) || first == '_') {
            for (int i = 1; i < value.length(); i++) {
                char c = value.charAt(i);
                if (!(Character.isLowerCase(c) || Character.isDigit(c) || c == '_' || c == '@')) {
                    return true;
                }
            }
            return false;
        }
        return true;
    }

    private static String escapeSingleQuoted(String value) {
        return value.replace("\\", "\\\\").replace("'", "\\'");
    }

    private static String escapeDoubleQuoted(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
