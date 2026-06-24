package io.smithy.beam.ir.erlang;

import java.util.List;

public final class ErlListComprehension implements ErlExpr {
    private final ErlExpr expression;
    private final ErlPattern generatorPattern;
    private final ErlExpr generatorExpr;
    private final ErlExpr filterOrNull;

    public ErlListComprehension(
            ErlExpr expression,
            ErlPattern generatorPattern,
            ErlExpr generatorExpr,
            ErlExpr filterOrNull) {
        this.expression = expression;
        this.generatorPattern = generatorPattern;
        this.generatorExpr = generatorExpr;
        this.filterOrNull = filterOrNull;
    }

    public static ErlListComprehension comprehension(
            ErlExpr expression, ErlPattern generatorPattern, ErlExpr generatorExpr) {
        return new ErlListComprehension(expression, generatorPattern, generatorExpr, null);
    }

    public ErlExpr expression() {
        return expression;
    }

    public ErlPattern generatorPattern() {
        return generatorPattern;
    }

    public ErlExpr generatorExpr() {
        return generatorExpr;
    }

    public ErlExpr filterOrNull() {
        return filterOrNull;
    }

    @Override
    public List<String> lines() {
        StringBuilder sb = new StringBuilder("[");
        sb.append(expression.asString());
        sb.append(" || ");
        sb.append(generatorPattern.asString());
        sb.append(" <- ");
        sb.append(generatorExpr.asString());
        if (filterOrNull != null) {
            sb.append(", ");
            sb.append(filterOrNull.asString());
        }
        sb.append(']');
        return List.of(sb.toString());
    }
}
