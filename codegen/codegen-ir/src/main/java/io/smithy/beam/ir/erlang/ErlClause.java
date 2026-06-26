package io.smithy.beam.ir.erlang;

import java.util.ArrayList;
import java.util.List;

public final class ErlClause implements IrObject {
    private final List<ErlPattern> patterns;
    private final List<ErlGuard> guards;
    private final List<ErlExpr> body;
    private final boolean forceBlockBody;

    public ErlClause(List<ErlPattern> patterns, List<ErlGuard> guards, List<ErlExpr> body, boolean forceBlockBody) {
        this.patterns = List.copyOf(patterns);
        this.guards = List.copyOf(guards);
        this.body = List.copyOf(body);
        this.forceBlockBody = forceBlockBody;
    }

    public ErlClause(List<ErlPattern> patterns, List<ErlGuard> guards, List<ErlExpr> body) {
        this(patterns, guards, body, false);
    }

    public static ErlClause clause(List<ErlPattern> patterns, List<ErlGuard> guards, ErlExpr... body) {
        return new ErlClause(patterns, guards, List.of(body));
    }

    public static ErlClause clause(List<ErlPattern> patterns, ErlExpr... body) {
        return clause(patterns, List.of(), body);
    }

    public static ErlClause blockClause(List<ErlPattern> patterns, List<ErlGuard> guards, ErlExpr... body) {
        return new ErlClause(patterns, guards, List.of(body), true);
    }

    public static ErlClause blockClause(List<ErlPattern> patterns, ErlExpr... body) {
        return blockClause(patterns, List.of(), body);
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

    public boolean forceBlockBody() {
        return forceBlockBody;
    }

    @Override
    public List<String> lines() {
        throw new UnsupportedOperationException("Use lines(indent, functionName, semicolon)");
    }

    public List<String> lines(int indent, String functionName, boolean semicolon) {
        List<String> out = new ArrayList<>();
        out.add(IrObject.indent(indent) + buildHead(functionName));
        if (isInlineBody()) {
            out.set(out.size() - 1, out.get(out.size() - 1) + " -> " + body.get(0).asString()
                    + (semicolon ? ";" : "."));
        } else {
            out.set(out.size() - 1, out.get(out.size() - 1) + " ->");
            for (ErlExpr expr : body) {
                if (expr.lines().size() == 1) {
                    out.add(IrObject.indent(indent + 1) + expr.asString());
                } else {
                    out.addAll(expr.lines(indent + 1));
                }
            }
            String last = out.get(out.size() - 1);
            out.set(out.size() - 1, last + (semicolon ? ";" : "."));
        }
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
        if (forceBlockBody) {
            return false;
        }
        return body.size() == 1 && body.get(0).lines().size() == 1;
    }
}
