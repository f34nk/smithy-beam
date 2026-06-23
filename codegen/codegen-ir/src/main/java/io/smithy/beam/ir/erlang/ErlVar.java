package io.smithy.beam.ir.erlang;

import java.util.List;

public final class ErlVar implements ErlExpr {
    private final String name;

    public ErlVar(String name) {
        this.name = name;
    }

    public String name() {
        return name;
    }

    @Override
    public List<String> lines() {
        return List.of(name);
    }
}
