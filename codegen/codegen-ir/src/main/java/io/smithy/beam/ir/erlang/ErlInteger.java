package io.smithy.beam.ir.erlang;

import java.util.List;

public final class ErlInteger implements ErlExpr {
    private final long value;

    public ErlInteger(long value) {
        this.value = value;
    }

    public long value() {
        return value;
    }

    @Override
    public List<String> lines() {
        return List.of(Long.toString(value));
    }
}
