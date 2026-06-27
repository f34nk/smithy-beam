package io.smithy.beam.ir.erlang;

import java.util.List;

public final class ErlMatchPattern implements ErlPattern {
    private final ErlPattern left;
    private final ErlPattern right;

    public ErlMatchPattern(ErlPattern left, ErlPattern right) {
        this.left = left;
        this.right = right;
    }

    public static ErlMatchPattern matchPattern(ErlPattern left, ErlPattern right) {
        return new ErlMatchPattern(left, right);
    }

    public ErlPattern left() {
        return left;
    }

    public ErlPattern right() {
        return right;
    }

    @Override
    public List<String> lines() {
        return List.of(left.asString() + " = " + right.asString());
    }
}
