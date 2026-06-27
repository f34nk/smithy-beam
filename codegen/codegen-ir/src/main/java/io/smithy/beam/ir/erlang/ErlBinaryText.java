package io.smithy.beam.ir.erlang;

public final class ErlBinaryText implements ErlBinarySegment {
    private final String text;

    public ErlBinaryText(String text) {
        this.text = text;
    }

    public static ErlBinaryText text(String text) {
        return new ErlBinaryText(text);
    }

    public String text() {
        return text;
    }

    public String asString() {
        return ErlString.string(text).asString();
    }
}
