package io.smithy.beam.ir.erlang;

import java.util.ArrayList;
import java.util.List;

public final class ErlTypeHeader implements IrObject {
    private final String name;
    private final List<ErlPreambleEntry> preamble;
    private final List<ErlHeaderEntry> entries;

    public ErlTypeHeader(String name, List<ErlPreambleEntry> preamble, List<ErlHeaderEntry> entries) {
        this.name = name;
        this.preamble = List.copyOf(preamble);
        this.entries = List.copyOf(entries);
    }

    public String name() {
        return name;
    }

    public List<ErlPreambleEntry> preamble() {
        return preamble;
    }

    public List<ErlHeaderEntry> entries() {
        return entries;
    }

    @Override
    public List<String> lines(int indent) {
        List<String> out = new ArrayList<>();
        for (ErlPreambleEntry item : preamble) {
            out.addAll(item.lines(indent));
        }
        for (int i = 0; i < entries.size(); i++) {
            if (i > 0) {
                out.add("");
            }
            out.addAll(entries.get(i).lines(indent));
        }
        return out;
    }

    @Override
    public List<String> lines() {
        return lines(0);
    }
}
