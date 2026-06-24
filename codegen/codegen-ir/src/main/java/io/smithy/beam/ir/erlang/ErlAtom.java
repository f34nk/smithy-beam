package io.smithy.beam.ir.erlang;

import java.util.List;

public final class ErlAtom implements ErlExpr {
    private final String value;

    public ErlAtom(String value) {
        this.value = value;
    }

    public static ErlAtom atom(String value) {
        return new ErlAtom(value);
    }

    public String value() {
        return value;
    }

    @Override
    public List<String> lines() {
        return List.of(renderAtom());
    }

    private String renderAtom() {
        if (needsQuotedAtom(value)) {
            return "'" + escapeSingleQuoted(value) + "'";
        }
        return value;
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
}
