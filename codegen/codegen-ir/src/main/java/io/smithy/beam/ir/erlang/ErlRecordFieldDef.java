package io.smithy.beam.ir.erlang;

import java.util.List;

public final class ErlRecordFieldDef implements IrObject {
    private final String name;
    private final String typeName;
    private final String defaultValue;
    private final List<ErlComment> preamble;

    public ErlRecordFieldDef(String name, String typeName) {
        this(name, typeName, null, List.of());
    }

    public ErlRecordFieldDef(String name, String typeName, List<ErlComment> preamble) {
        this(name, typeName, null, preamble);
    }

    public ErlRecordFieldDef(String name, String typeName, String defaultValue, List<ErlComment> preamble) {
        this.name = name;
        this.typeName = typeName;
        this.defaultValue = defaultValue;
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
        String lhs = defaultValue != null ? name + " = " + defaultValue : name;
        return List.of(IrObject.indent(indent) + lhs + " :: " + typeName);
    }

    @Override
    public List<String> lines() {
        return lines(0);
    }
}
