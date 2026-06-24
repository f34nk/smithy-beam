package io.smithy.beam.ir.erlang;

import java.util.List;

public final class ErlComment implements ErlPreambleEntry {
    private final String text;

    public ErlComment(String text) {
        this.text = text;
    }

    public String text() {
        return text;
    }

    public static ErlComment comment(String text) {
        return new ErlComment(text);
    }

    @Override
    public List<String> lines(int indent) {
        return ErlLayout.renderPercentComment(text, indent);
    }

    @Override
    public List<String> lines() {
        return lines(0);
    }
}
