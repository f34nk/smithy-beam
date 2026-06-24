package io.smithy.beam.ir.erlang;

import java.util.ArrayList;
import java.util.List;

public final class ErlClause implements IrObject {
    private final List<ErlPattern> patterns;
    private final List<ErlGuard> guards;
    private final List<ErlExpr> body;

    public ErlClause(List<ErlPattern> patterns, List<ErlGuard> guards, List<ErlExpr> body) {
        this.patterns = List.copyOf(patterns);
        this.guards = List.copyOf(guards);
        this.body = List.copyOf(body);
    }

    public static ErlClause clause(List<ErlPattern> patterns, List<ErlGuard> guards, ErlExpr... body) {
        return new ErlClause(patterns, guards, List.of(body));
    }

    public static ErlClause clause(List<ErlPattern> patterns, ErlExpr... body) {
        return clause(patterns, List.of(), body);
    }

    public List<ErlPattern> patterns() {
        return patterns;
    }

    public List<ErlGuard> guards() {
        return guards;
    }

    public List<ErlExpr> body() {
        return body;
    }

    @Override
    public List<String> lines() {
        throw new UnsupportedOperationException("Use lines(indent, functionName, semicolon)");
    }

    public List<String> lines(int indent, String functionName, boolean semicolon) {
        List<String> out = new ArrayList<>();
        if (needsMultilineHead()) {
            out.addAll(buildMultilineHeadLines(indent, functionName));
        } else {
            out.add(IrObject.indent(indent) + buildHead(functionName));
        }
        if (isInlineBody()) {
            out.set(out.size() - 1, out.get(out.size() - 1) + " -> " + body.get(0).asString()
                    + (semicolon ? ";" : "."));
        } else {
            out.set(out.size() - 1, out.get(out.size() - 1) + " ->");
            for (ErlExpr expr : body) {
                out.addAll(expr.lines(indent + 1));
            }
            String last = out.get(out.size() - 1);
            out.set(out.size() - 1, last + (semicolon ? ";" : "."));
        }
        return out;
    }

    private boolean needsMultilineHead() {
        if (patterns.size() > 1) {
            return true;
        }
        if (patterns.size() == 1 && patterns.get(0) instanceof ErlRecordPattern recordPattern) {
            return recordPattern.aliasOrNull() != null;
        }
        return false;
    }

    private List<String> buildMultilineHeadLines(int indent, String functionName) {
        List<String> out = new ArrayList<>();
        out.add(IrObject.indent(indent) + functionName + "(");
        for (int i = 0; i < patterns.size(); i++) {
            boolean lastArg = i == patterns.size() - 1;
            String suffix = lastArg ? "" : ",";
            ErlPattern pattern = patterns.get(i);
            if (pattern instanceof ErlRecordPattern recordPattern && recordPattern.aliasOrNull() != null) {
                out.add(IrObject.indent(indent + 1) + recordPattern.aliasOrNull() + " = #" + recordPattern.name() + "{");
                List<ErlRecordFieldPattern> fields = recordPattern.fields();
                for (int j = 0; j < fields.size(); j++) {
                    String fieldSuffix = j < fields.size() - 1 ? "," : "";
                    out.add(IrObject.indent(indent + 2) + fields.get(j).asString() + fieldSuffix);
                }
                out.add(IrObject.indent(indent + 1) + "}" + suffix);
            } else {
                out.add(IrObject.indent(indent + 1) + pattern.asString() + suffix);
            }
        }
        String closing = ")";
        if (!guards.isEmpty()) {
            closing += " when " + guardText();
        }
        out.add(IrObject.indent(indent) + closing);
        return out;
    }

    private String guardText() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < guards.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(guards.get(i).asString());
        }
        return sb.toString();
    }

    private String buildHead(String functionName) {
        StringBuilder sb = new StringBuilder(functionName);
        sb.append('(');
        for (int i = 0; i < patterns.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(patterns.get(i).asString());
        }
        sb.append(')');
        if (!guards.isEmpty()) {
            sb.append(" when ").append(guardText());
        }
        return sb.toString();
    }

    private boolean isInlineBody() {
        return body.size() == 1 && body.get(0).lines().size() == 1;
    }
}
