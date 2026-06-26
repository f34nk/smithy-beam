package io.smithy.beam.ir.erlang;

import java.util.List;

public final class ErlMapPattern implements ErlPattern {
    private final List<ErlMapFieldPattern> fields;

    public ErlMapPattern(List<ErlMapFieldPattern> fields) {
        this.fields = List.copyOf(fields);
    }

    public static ErlMapPattern mapPattern(ErlMapFieldPattern... fields) {
        return new ErlMapPattern(List.of(fields));
    }

    @Override
    public List<String> lines() {
        StringBuilder sb = new StringBuilder("#{");
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(fields.get(i).asString());
        }
        sb.append("}");
        return List.of(sb.toString());
    }
}
