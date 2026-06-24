package io.smithy.beam.ir.erlang;

import java.util.List;

public final class ErlAtomPattern implements ErlPattern {
    private final String value;

    public ErlAtomPattern(String value) {
        this.value = value;
    }

    public static ErlAtomPattern atomPattern(String value) {
        return new ErlAtomPattern(value);
    }

    public String value() {
        return value;
    }

    @Override
    public List<String> lines() {
        return List.of(ErlLayout.renderAtom(value));
    }
}
