package io.smithy.beam.ir.erlang;

import java.util.ArrayList;
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
        return lines(0);
    }

    @Override
    public List<String> lines(int indent) {
        if (expr.lines().size() == 1) {
            return List.of(IrObject.indent(indent) + pattern.asString() + " = " + expr.asString());
        }
        List<String> out = new ArrayList<>();
        out.add(IrObject.indent(indent) + pattern.asString() + " =");
        out.addAll(expr.lines(indent + 1));
        return out;
    }
}
