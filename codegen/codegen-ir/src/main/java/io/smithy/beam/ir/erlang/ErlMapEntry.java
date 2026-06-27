package io.smithy.beam.ir.erlang;

public final class ErlMapEntry {
    private final ErlExpr key;
    private final ErlExpr value;

    public ErlMapEntry(ErlExpr key, ErlExpr value) {
        this.key = key;
        this.value = value;
    }

    public static ErlMapEntry entry(ErlExpr key, ErlExpr value) {
        return new ErlMapEntry(key, value);
    }

    public ErlExpr key() {
        return key;
    }

    public ErlExpr value() {
        return value;
    }

    public String asString() {
        return key.asString() + " => " + value.asString();
    }
}
