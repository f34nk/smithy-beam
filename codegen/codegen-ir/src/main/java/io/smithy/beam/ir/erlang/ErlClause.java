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
        String head = buildHead(functionName);
        if (isInlineBody()) {
            out.add(ErlLayout.indent(indent) + head + " -> " + body.get(0).asString()
                    + (semicolon ? ";" : "."));
        } else {
            out.add(ErlLayout.indent(indent) + head + " ->");
            for (ErlExpr expr : body) {
                out.addAll(expr.lines(indent + 1));
            }
            String last = out.get(out.size() - 1);
            out.set(out.size() - 1, last + (semicolon ? ";" : "."));
        }
        return out;
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
            sb.append(" when ");
            for (int i = 0; i < guards.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(guards.get(i).asString());
            }
        }
        return sb.toString();
    }

    private boolean isInlineBody() {
        return body.size() == 1 && body.get(0).lines().size() == 1;
    }
}
