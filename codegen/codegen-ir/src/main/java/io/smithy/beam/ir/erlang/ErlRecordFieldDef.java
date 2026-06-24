package io.smithy.beam.ir.erlang;

import java.util.List;

public final class ErlRecordFieldDef implements IrObject {
    private final String name;
    private final String typeName;

    public ErlRecordFieldDef(String name, String typeName) {
        this.name = name;
        this.typeName = typeName;
    }

    public String name() {
        return name;
    }

    public String typeName() {
        return typeName;
    }

    @Override
    public List<String> lines(int indent) {
        return List.of(IrObject.indent(indent) + name + "  :: " + typeName);
    }

    @Override
    public List<String> lines() {
        return lines(0);
    }
}
