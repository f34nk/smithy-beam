package io.smithy.beam.ir.erlang;

import java.util.List;

public final class ErlModuleDoc implements ErlPreambleEntry {
    private final String text;

    public ErlModuleDoc(String text) {
        this.text = text;
    }

    public String text() {
        return text;
    }

    public static ErlModuleDoc moduleDoc(String text) {
        return new ErlModuleDoc(text);
    }

    @Override
    public List<String> lines(int indent) {
        return ErlLayout.renderDocAttribute("moduledoc", text, indent);
    }

    @Override
    public List<String> lines() {
        return lines(0);
    }
}
