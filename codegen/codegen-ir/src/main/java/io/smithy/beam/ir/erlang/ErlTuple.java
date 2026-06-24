package io.smithy.beam.ir.erlang;

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
