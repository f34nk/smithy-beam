package io.smithy.beam.ir.erlang;

import java.util.ArrayList;
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
        String marker = IrObject.indent(indent) + "%%";
        if (!text.contains("\n")) {
            if (text.isEmpty()) {
                return List.of(marker);
            }
            return List.of(marker + " " + text);
        }
        List<String> out = new ArrayList<>();
        for (String line : text.split("\n", -1)) {
            if (line.isEmpty()) {
                out.add(marker);
            } else {
                out.add(marker + " " + line);
            }
        }
        return out;
    }

    @Override
    public List<String> lines() {
        return lines(0);
    }
}
