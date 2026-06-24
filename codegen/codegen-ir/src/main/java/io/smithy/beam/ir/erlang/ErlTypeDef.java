package io.smithy.beam.ir.erlang;

import java.util.List;

public final class ErlTypeDef implements ErlHeaderEntry {
    private final String name;
    private final String body;

    public ErlTypeDef(String name, String body) {
        this.name = name;
        this.body = body;
    }

    public String name() {
        return name;
    }

    public String body() {
        return body;
    }

    @Override
    public List<String> lines(int indent) {
        return List.of(IrObject.indent(indent) + "-type " + name + "() :: " + body + ".");
    }

    @Override
    public List<String> lines() {
        return lines(0);
    }
}
