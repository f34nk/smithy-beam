package io.smithy.beam.ir.erlang;

public final class ErlRecordFieldPattern {
    private final String name;
    private final ErlPattern pattern;

    public ErlRecordFieldPattern(String name, ErlPattern pattern) {
        this.name = name;
        this.pattern = pattern;
    }

    public static ErlRecordFieldPattern fieldPattern(String name, ErlPattern pattern) {
        return new ErlRecordFieldPattern(name, pattern);
    }

    public String name() {
        return name;
    }

    public ErlPattern pattern() {
        return pattern;
    }

    public String asString() {
        return name + " = " + pattern.asString();
    }
}
