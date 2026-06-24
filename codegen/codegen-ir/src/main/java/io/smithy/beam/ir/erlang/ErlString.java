package io.smithy.beam.ir.erlang;

import java.util.List;

public final class ErlString implements ErlExpr {
    private final String value;

    public ErlString(String value) {
        this.value = value;
    }

    public static ErlString string(String value) {
        return new ErlString(value);
    }

    public String value() {
        return value;
    }

    @Override
    public List<String> lines() {
        return List.of(ErlLayout.renderString(value));
    }
}
