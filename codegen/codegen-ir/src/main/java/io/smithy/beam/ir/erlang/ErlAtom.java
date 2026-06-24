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
        return List.of(ErlLayout.renderAtom(value));
    }
}
