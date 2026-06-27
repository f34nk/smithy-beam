package io.smithy.beam.ir.erlang;

import java.util.List;

public final class ErlBinaryPattern implements ErlPattern {
    private final String value;

    public ErlBinaryPattern(String value) {
        this.value = value;
    }

    public static ErlBinaryPattern binaryPattern(String value) {
        return new ErlBinaryPattern(value);
    }

    public String value() {
        return value;
    }

    @Override
    public List<String> lines() {
        if (value.isEmpty()) {
            return List.of("<<>>");
        }
        return List.of("<<" + ErlString.string(value).asString() + ">>");
    }
}
