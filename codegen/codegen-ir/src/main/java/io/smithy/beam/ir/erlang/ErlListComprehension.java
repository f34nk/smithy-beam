package io.smithy.beam.ir.erlang;

import java.util.ArrayList;
import java.util.List;

public final class ErlListComprehension implements ErlExpr {
    private final ErlExpr expression;
    private final ErlPattern generatorPattern;
    private final ErlExpr generatorExpr;
    private final List<ErlExpr> filters;
    private final List<ErlComprehensionQual> qualifiersOrNull;

    private ErlListComprehension(
            ErlExpr expression,
            ErlPattern generatorPattern,
            ErlExpr generatorExpr,
            List<ErlExpr> filters,
            List<ErlComprehensionQual> qualifiersOrNull) {
        this.expression = expression;
        this.generatorPattern = generatorPattern;
        this.generatorExpr = generatorExpr;
        this.filters = List.copyOf(filters);
        this.qualifiersOrNull = qualifiersOrNull == null ? null : List.copyOf(qualifiersOrNull);
    }

    public static ErlListComprehension comprehension(
            ErlExpr expression, ErlPattern generatorPattern, ErlExpr generatorExpr) {
        return new ErlListComprehension(expression, generatorPattern, generatorExpr, List.of(), null);
    }

    public static ErlListComprehension comprehension(
            ErlExpr expression,
            ErlPattern generatorPattern,
            ErlExpr generatorExpr,
            ErlExpr filter) {
        return new ErlListComprehension(expression, generatorPattern, generatorExpr, List.of(filter), null);
    }

    public static ErlListComprehension comprehensionWithFilters(
            ErlExpr expression,
            ErlPattern generatorPattern,
            ErlExpr generatorExpr,
            List<ErlExpr> filters) {
        return new ErlListComprehension(expression, generatorPattern, generatorExpr, filters, null);
    }

    public static ErlListComprehension comprehensionQualifiers(
            ErlExpr expression, List<ErlComprehensionQual> qualifiers) {
        return new ErlListComprehension(expression, null, null, List.of(), qualifiers);
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

    public List<ErlExpr> filters() {
        return filters;
    }

    public List<ErlComprehensionQual> qualifiersOrNull() {
        return qualifiersOrNull;
    }

    @Override
    public List<String> lines() {
        return lines(0);
    }

    @Override
    public List<String> lines(int indent) {
        if (qualifiersOrNull != null) {
            return qualifierLines(indent);
        }

        List<String> exprLines = expression.lines(indent + 1);
        if (exprLines.size() == 1 && filters.size() <= 1) {
            StringBuilder sb = new StringBuilder("[");
            sb.append(expression.asString());
            sb.append(" || ");
            sb.append(generatorPattern.asString());
            sb.append(" <- ");
            sb.append(generatorExpr.asString());
            for (ErlExpr filter : filters) {
                sb.append(", ");
                sb.append(filter.asString());
            }
            sb.append(']');
            return List.of(sb.toString());
        }

        List<String> out = new ArrayList<>();
        out.add(IrObject.indent(indent) + "[");
        out.addAll(exprLines);
        StringBuilder genLine = new StringBuilder(IrObject.indent(indent) + " || ");
        genLine.append(generatorPattern.asString());
        genLine.append(" <- ");
        genLine.append(generatorExpr.asString());
        if (!filters.isEmpty()) {
            genLine.append(',');
            out.add(genLine.toString());
            for (int i = 0; i < filters.size(); i++) {
                String suffix = (i < filters.size() - 1) ? "," : "";
                out.add(IrObject.indent(indent + 1) + filters.get(i).asString() + suffix);
            }
        } else {
            out.add(genLine.toString());
        }
        out.add(IrObject.indent(indent) + "]");
        return out;
    }

    private List<String> qualifierLines(int indent) {
        List<String> exprLines = expression.lines(indent + 1);
        if (exprLines.size() == 1 && fitsSingleLine(qualifiersOrNull)) {
            StringBuilder sb = new StringBuilder("[");
            sb.append(expression.asString());
            appendQualifiers(sb);
            sb.append(']');
            return List.of(sb.toString());
        }

        List<String> out = new ArrayList<>();
        out.add(IrObject.indent(indent) + "[");
        out.addAll(exprLines);
        StringBuilder genLine = new StringBuilder(IrObject.indent(indent) + " ");
        appendQualifiers(genLine);
        out.add(genLine.toString());
        out.add(IrObject.indent(indent) + "]");
        return out;
    }

    private static boolean fitsSingleLine(List<ErlComprehensionQual> qualifiers) {
        for (ErlComprehensionQual qual : qualifiers) {
            if (qual instanceof ErlComprehensionGenerator generator
                    && generator.expr().lines().size() > 1) {
                return false;
            }
        }
        return true;
    }

    private void appendQualifiers(StringBuilder sb) {
        boolean sawGenerator = false;
        for (ErlComprehensionQual qual : qualifiersOrNull) {
            if (qual instanceof ErlComprehensionGenerator generator) {
                if (!sawGenerator) {
                    sb.append(" || ");
                    sawGenerator = true;
                } else {
                    sb.append(", ");
                }
                sb.append(generator.pattern().asString());
                sb.append(" <- ");
                sb.append(generator.expr().asString());
            } else if (qual instanceof ErlComprehensionFilter filter) {
                sb.append(", ");
                sb.append(filter.filter().asString());
            }
        }
    }
}
