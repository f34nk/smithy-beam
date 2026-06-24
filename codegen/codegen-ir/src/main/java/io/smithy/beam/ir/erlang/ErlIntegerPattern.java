package io.smithy.beam.ir.erlang;

import java.util.List;

public final class ErlIntegerPattern implements ErlPattern {
    private final long value;

    public ErlIntegerPattern(long value) {
        this.value = value;
    }

    public static ErlIntegerPattern integerPattern(long value) {
        return new ErlIntegerPattern(value);
    }

    public long value() {
        return value;
    }

    @Override
    public List<String> lines() {
        return List.of(Long.toString(value));
    }
}
