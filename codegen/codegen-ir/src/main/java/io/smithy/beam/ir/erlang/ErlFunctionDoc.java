package io.smithy.beam.ir.erlang;

import java.util.ArrayList;
import java.util.List;

public final class ErlFunctionDoc implements ErlFunctionPreambleEntry {
    private final String text;

    public ErlFunctionDoc(String text) {
        this.text = text;
    }

    public String text() {
        return text;
    }

    public static ErlFunctionDoc functionDoc(String text) {
        return new ErlFunctionDoc(text);
    }

    @Override
    public List<String> lines(int indent) {
        return renderDocAttribute("doc", text, indent);
    }

    private static List<String> renderDocAttribute(String attribute, String text, int indent) {
        String head = IrObject.indent(indent) + "-" + attribute;
        if (!text.contains("\n")) {
            return List.of(head + " " + ErlString.string(text).asString() + ".");
        }
        List<String> out = new ArrayList<>();
        out.add(head + " \"\"\"");
        for (String line : text.split("\n", -1)) {
            out.add(IrObject.indent(indent) + line);
        }
        out.add(IrObject.indent(indent) + "\"\"\".");
        return out;
    }

    @Override
    public List<String> lines() {
        return lines(0);
    }
}
