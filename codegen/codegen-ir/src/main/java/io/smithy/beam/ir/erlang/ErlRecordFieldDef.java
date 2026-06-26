package io.smithy.beam.ir.erlang;

import java.util.List;

public final class ErlRecordFieldDef implements IrObject {
    private final String name;
    private final String typeName;
    private final List<ErlComment> preamble;

    public ErlRecordFieldDef(String name, String typeName) {
        this(name, typeName, List.of());
    }

    public ErlRecordFieldDef(String name, String typeName, List<ErlComment> preamble) {
        this.name = name;
        this.typeName = typeName;
        this.preamble = List.copyOf(preamble);
    }

    public String name() {
        return name;
    }

    public String typeName() {
        return typeName;
    }

    public List<ErlComment> preamble() {
        return preamble;
    }

    @Override
    public List<String> lines(int indent) {
        return List.of(IrObject.indent(indent) + name + " :: " + typeName);
    }

    @Override
    public List<String> lines() {
        return lines(0);
    }
}
