package io.smithy.beam.ir.erlang;

import java.util.ArrayList;
import java.util.List;

public final class ErlRecord implements ErlExpr {
    private final String name;
    private final ErlExpr recordOrNull;
    private final List<ErlRecordField> fields;

    public ErlRecord(String name, ErlExpr recordOrNull, List<ErlRecordField> fields) {
        this.name = name;
        this.recordOrNull = recordOrNull;
        this.fields = List.copyOf(fields);
    }

    public static ErlRecord record(String name, ErlRecordField... fields) {
        return new ErlRecord(name, null, List.of(fields));
    }

    public static ErlRecord recordUpdate(ErlExpr record, String name, ErlRecordField... fields) {
        return new ErlRecord(name, record, List.of(fields));
    }

    public String name() {
        return name;
    }

    public ErlExpr recordOrNull() {
        return recordOrNull;
    }

    public List<ErlRecordField> fields() {
        return fields;
    }

    @Override
    public List<String> lines(int indent) {
        List<String> out = new ArrayList<>();
        String open;
        if (recordOrNull != null) {
            open = recordOrNull.asString() + "#" + name + "{";
        } else {
            open = "#" + name + "{";
        }
        out.add(ErlLayout.indent(indent) + open);
        for (int i = 0; i < fields.size(); i++) {
            String suffix = (i < fields.size() - 1) ? "," : "";
            out.add(ErlLayout.indent(indent + 1)
                    + fields.get(i).name() + " = "
                    + fields.get(i).value().asString() + suffix);
        }
        out.add(ErlLayout.indent(indent) + "}");
        return out;
    }

    @Override
    public List<String> lines() {
        return lines(0);
    }
}
