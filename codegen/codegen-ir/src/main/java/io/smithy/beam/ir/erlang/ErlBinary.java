package io.smithy.beam.ir.erlang;

import java.util.List;

public final class ErlBinary implements ErlExpr {
    private final String value;

    public ErlBinary(String value) {
        this.value = value;
    }

    public static ErlBinary binary(String value) {
        return new ErlBinary(value);
    }

    public String value() {
        return value;
    }

    @Override
    public List<String> lines() {
        return List.of(ErlLayout.renderBinaryLiteral(value));
    }
}
