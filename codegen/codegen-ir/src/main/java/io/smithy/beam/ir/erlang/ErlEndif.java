package io.smithy.beam.ir.erlang;

import java.util.List;

public final class ErlEndif implements ErlHeaderEntry {
    @Override
    public List<String> lines(int indent) {
        return List.of(IrObject.indent(indent) + "-endif.");
    }

    @Override
    public List<String> lines() {
        return lines(0);
    }
}
