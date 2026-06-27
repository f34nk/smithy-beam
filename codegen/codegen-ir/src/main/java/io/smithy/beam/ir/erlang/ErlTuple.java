package io.smithy.beam.ir.erlang;

import java.util.ArrayList;
import java.util.List;

public final class ErlTuple implements ErlExpr {
    private final List<ErlExpr> elements;

    public ErlTuple(List<ErlExpr> elements) {
        this.elements = List.copyOf(elements);
    }

    public static ErlTuple tuple(ErlExpr... elements) {
        return new ErlTuple(List.of(elements));
    }

    public List<ErlExpr> elements() {
        return elements;
    }

    @Override
    public List<String> lines() {
        return lines(0);
    }

    @Override
    public List<String> lines(int indent) {
        if (elements.size() == 2
                && elements.get(0).lines().size() == 1
                && elements.get(1) instanceof ErlRecord record
                && !record.fields().isEmpty()) {
            List<String> out = new ArrayList<>();
            out.add(IrObject.indent(indent) + "{" + elements.get(0).asString() + ", #" + record.name() + "{");
            List<ErlRecordField> fields = record.fields();
            for (int i = 0; i < fields.size(); i++) {
                String suffix = i < fields.size() - 1 ? "," : "";
                out.add(IrObject.indent(indent + 1)
                        + fields.get(i).name() + " = "
                        + fields.get(i).value().asString() + suffix);
            }
            out.add(IrObject.indent(indent) + "}}");
            return out;
        }
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < elements.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(elements.get(i).asString());
        }
        sb.append('}');
        return List.of(sb.toString());
    }
}
