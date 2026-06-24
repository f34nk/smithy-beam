package io.smithy.beam.ir.erlang;

import java.util.List;

public final class ErlVarPattern implements ErlPattern {
    private final String name;

    public ErlVarPattern(String name) {
        this.name = name;
    }

    public static ErlVarPattern varPattern(String name) {
        return new ErlVarPattern(name);
    }

    public String name() {
        return name;
    }

    @Override
    public List<String> lines() {
        return List.of(name);
    }
}
