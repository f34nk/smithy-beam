package io.smithy.beam.ir.erlang;

import java.util.List;

public final class ErlMatch implements ErlExpr {
    private final ErlPattern pattern;
    private final ErlExpr expr;

    public ErlMatch(ErlPattern pattern, ErlExpr expr) {
        this.pattern = pattern;
        this.expr = expr;
    }

    public static ErlMatch match(ErlPattern pattern, ErlExpr expr) {
        return new ErlMatch(pattern, expr);
    }

    public ErlPattern pattern() {
        return pattern;
    }

    public ErlExpr expr() {
        return expr;
    }

    @Override
    public List<String> lines() {
        return List.of(pattern.asString() + " = " + expr.asString());
    }
}
