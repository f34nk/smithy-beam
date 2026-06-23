package io.smithy.beam.ir.erlang;

import java.util.ArrayList;
import java.util.List;

public final class ErlRecordField implements IrObject {
    private final String name;
    private final ErlExpr value;

    public ErlRecordField(String name, ErlExpr value) {
        this.name = name;
        this.value = value;
    }

    public String name() {
        return name;
    }

    public ErlExpr value() {
        return value;
    }

    @Override
    public List<String> lines(int indent) {
        return List.of(name + " = " + value.asString());
    }

    @Override
    public List<String> lines() {
        return lines(0);
    }
}
