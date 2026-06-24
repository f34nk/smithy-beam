package io.smithy.beam.ir.erlang;

import java.util.ArrayList;
import java.util.List;

public final class ErlCase implements ErlExpr {
    private final ErlExpr scrutinee;
    private final List<ErlClause> clauses;

    public ErlCase(ErlExpr scrutinee, List<ErlClause> clauses) {
        this.scrutinee = scrutinee;
        this.clauses = List.copyOf(clauses);
    }

    public static ErlCase caseExpr(ErlExpr scrutinee, ErlClause... clauses) {
        return new ErlCase(scrutinee, List.of(clauses));
    }

    public ErlExpr scrutinee() {
        return scrutinee;
    }

    public List<ErlClause> clauses() {
        return clauses;
    }

    @Override
    public List<String> lines(int indent) {
        List<String> out = new ArrayList<>();
        out.add(IrObject.indent(indent) + "case " + scrutinee.asString() + " of");
        for (int i = 0; i < clauses.size(); i++) {
            out.addAll(caseClauseLines(clauses.get(i), indent + 1, i < clauses.size() - 1));
        }
        out.add(IrObject.indent(indent) + "end");
        return out;
    }

    @Override
    public List<String> lines() {
        return lines(0);
    }

    private static List<String> caseClauseLines(ErlClause clause, int indent, boolean semicolon) {
        List<String> out = new ArrayList<>();
        String head = clauseHead(clause);
        if (isInlineBody(clause)) {
            out.add(IrObject.indent(indent) + head + " -> " + clause.body().get(0).asString()
                    + (semicolon ? ";" : ""));
        } else {
            out.add(IrObject.indent(indent) + head + " ->");
            for (ErlExpr expr : clause.body()) {
                out.addAll(expr.lines(indent + 1));
            }
            String last = out.get(out.size() - 1);
            out.set(out.size() - 1, last + (semicolon ? ";" : ""));
        }
        return out;
    }

    private static String clauseHead(ErlClause clause) {
        StringBuilder sb = new StringBuilder();
        List<ErlPattern> patterns = clause.patterns();
        for (int i = 0; i < patterns.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(patterns.get(i).asString());
        }
        List<ErlGuard> guards = clause.guards();
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

    private static boolean isInlineBody(ErlClause clause) {
        return clause.body().size() == 1 && clause.body().get(0).lines().size() == 1;
    }
}
