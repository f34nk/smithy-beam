package io.smithy.beam.ir.erlang;

public final class ErlComprehensionGenerator implements ErlComprehensionQual {
    private final ErlPattern pattern;
    private final ErlExpr expr;

    public ErlComprehensionGenerator(ErlPattern pattern, ErlExpr expr) {
        this.pattern = pattern;
        this.expr = expr;
    }

    public ErlPattern pattern() {
        return pattern;
    }

    public ErlExpr expr() {
        return expr;
    }
}
