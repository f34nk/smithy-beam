package io.smithy.beam.ir.erlang;

import java.util.ArrayList;
import java.util.List;

public final class ErlRecord implements ErlExpr {
    private final String name;
    private final ErlExpr recordOrNull;
    private final List<ErlRecordField> fields;
    private final boolean compact;

    public ErlRecord(String name, ErlExpr recordOrNull, List<ErlRecordField> fields, boolean compact) {
        this.name = name;
        this.recordOrNull = recordOrNull;
        this.fields = List.copyOf(fields);
        this.compact = compact;
    }

    public ErlRecord(String name, ErlExpr recordOrNull, List<ErlRecordField> fields) {
        this(name, recordOrNull, fields, false);
    }

    public static ErlRecord record(String name, ErlRecordField... fields) {
        return new ErlRecord(name, null, List.of(fields));
    }

    public static ErlRecord recordCompact(String name, ErlRecordField... fields) {
        return new ErlRecord(name, null, List.of(fields), true);
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
        if (fields.isEmpty()) {
            return List.of(renderEmptyRecord(indent));
        }
        if (compact || (recordOrNull != null && fields.size() == 1)) {
            return List.of(renderSingleLine(indent));
        }
        List<String> out = new ArrayList<>();
        String open;
        if (recordOrNull != null) {
            open = recordOrNull.asString() + "#" + name + "{";
        } else {
            open = "#" + name + "{";
        }
        out.add(IrObject.indent(indent) + open);
        for (int i = 0; i < fields.size(); i++) {
            String suffix = (i < fields.size() - 1) ? "," : "";
            out.add(IrObject.indent(indent + 1)
                    + fields.get(i).name() + " = "
                    + fields.get(i).value().asString() + suffix);
        }
        out.add(IrObject.indent(indent) + "}");
        return out;
    }

    private String renderEmptyRecord(int indent) {
        StringBuilder sb = new StringBuilder(IrObject.indent(indent));
        if (recordOrNull != null) {
            sb.append(recordOrNull.asString()).append('#').append(name).append("{}");
        } else {
            sb.append('#').append(name).append("{}");
        }
        return sb.toString();
    }

    private String renderSingleLine(int indent) {
        StringBuilder sb = new StringBuilder(IrObject.indent(indent));
        if (recordOrNull != null) {
            sb.append(recordOrNull.asString()).append('#').append(name).append("{ ");
        } else {
            sb.append('#').append(name).append("{ ");
        }
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(fields.get(i).name()).append(" = ").append(fields.get(i).value().asString());
        }
        sb.append(" }");
        return sb.toString();
    }

    @Override
    public List<String> lines() {
        return lines(0);
    }
}
