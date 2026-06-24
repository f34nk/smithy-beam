package io.smithy.beam.ir.erlang;

import java.util.List;

public final class ErlFunctionDoc implements IrObject {
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
        return ErlLayout.renderDocAttribute("doc", text, indent);
    }

    @Override
    public List<String> lines() {
        return lines(0);
    }
}
