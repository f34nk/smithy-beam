package io.smithy.beam.ir.erlang;

import java.util.List;

public final class ErlBlankLine implements ErlHeaderEntry {
    @Override
    public List<String> lines(int indent) {
        return List.of("");
    }

    @Override
    public List<String> lines() {
        return lines(0);
    }
}
