package io.smithy.beam.ir.erlang;

import java.util.List;

public final class ErlMapUpdate implements ErlExpr {
    private final ErlExpr map;
    private final List<ErlMapEntry> entries;

    public ErlMapUpdate(ErlExpr map, List<ErlMapEntry> entries) {
        this.map = map;
        this.entries = List.copyOf(entries);
    }

    public static ErlMapUpdate mapUpdate(ErlExpr map, ErlMapEntry... entries) {
        return new ErlMapUpdate(map, List.of(entries));
    }

    @Override
    public List<String> lines() {
        StringBuilder sb = new StringBuilder(map.asString()).append("#{");
        for (int i = 0; i < entries.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(entries.get(i).asString());
        }
        sb.append("}");
        return List.of(sb.toString());
    }
}
