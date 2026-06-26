package io.smithy.beam.ir.erlang;

import java.util.ArrayList;
import java.util.List;

public final class ErlRecordDef implements ErlHeaderEntry {
    private final String name;
    private final List<ErlRecordFieldDef> fields;

    public ErlRecordDef(String name, List<ErlRecordFieldDef> fields) {
        this.name = name;
        this.fields = List.copyOf(fields);
    }

    public String name() {
        return name;
    }

    public List<ErlRecordFieldDef> fields() {
        return fields;
    }

    @Override
    public List<String> lines(int indent) {
        List<String> out = new ArrayList<>();
        if (fields.isEmpty()) {
            out.add(IrObject.indent(indent) + "-record(" + name + ", {}).");
            return out;
        }
        out.add(IrObject.indent(indent) + "-record(" + name + ", {");
        for (int i = 0; i < fields.size(); i++) {
            String comma = (i < fields.size() - 1) ? "," : "";
            ErlRecordFieldDef field = fields.get(i);
            for (ErlComment comment : field.preamble()) {
                out.addAll(comment.lines(indent + 1));
            }
            out.add(IrObject.indent(indent + 1) + field.name() + " :: " + field.typeName() + comma);
        }
        out.add(IrObject.indent(indent) + "}).");
        return out;
    }

    @Override
    public List<String> lines() {
        return lines(0);
    }
}
