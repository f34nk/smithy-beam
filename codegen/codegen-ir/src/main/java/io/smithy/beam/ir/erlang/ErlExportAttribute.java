package io.smithy.beam.ir.erlang;

import java.util.ArrayList;
import java.util.List;

public final class ErlExportAttribute implements ErlModuleAttribute {
    private static final int EXPORT_LINE_LIMIT = 100;

    private final List<String> exports;

    public ErlExportAttribute(List<String> exports) {
        this.exports = List.copyOf(exports);
    }

    public static ErlExportAttribute export(List<String> exports) {
        return new ErlExportAttribute(exports);
    }

    public List<String> exports() {
        return exports;
    }

    @Override
    public List<String> lines(int indent) {
        if (exports.isEmpty()) {
            return List.of(IrObject.indent(indent) + "-export([]).");
        }
        String joined = String.join(", ", exports);
        if (joined.length() <= EXPORT_LINE_LIMIT) {
            return List.of(IrObject.indent(indent) + "-export([" + joined + "]).");
        }
        List<String> out = new ArrayList<>();
        out.add(IrObject.indent(indent) + "-export([");
        for (int i = 0; i < exports.size(); i++) {
            String suffix = (i < exports.size() - 1) ? "," : "";
            out.add(IrObject.indent(indent + 1) + exports.get(i) + suffix);
        }
        out.add(IrObject.indent(indent) + "]).");
        return out;
    }

    @Override
    public List<String> lines() {
        return lines(0);
    }
}
