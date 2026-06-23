package io.smithy.beam.ir.erlang;

import java.util.ArrayList;
import java.util.List;

public final class ErlFunction implements IrObject {
    private final String name;
    private final int arity;
    private final ErlFunctionSpec specOrNull;
    private final List<ErlClause> clauses;

    public ErlFunction(String name, int arity, ErlFunctionSpec specOrNull, List<ErlClause> clauses) {
        this.name = name;
        this.arity = arity;
        this.specOrNull = specOrNull;
        this.clauses = List.copyOf(clauses);
    }

    public String name() {
        return name;
    }

    public int arity() {
        return arity;
    }

    public ErlFunctionSpec specOrNull() {
        return specOrNull;
    }

    public List<ErlClause> clauses() {
        return clauses;
    }

    @Override
    public List<String> lines(int indent) {
        List<String> out = new ArrayList<>();
        if (specOrNull != null) {
            out.addAll(specOrNull.lines(indent));
        }
        for (int i = 0; i < clauses.size(); i++) {
            out.addAll(clauses.get(i).lines(indent, name, i < clauses.size() - 1));
        }
        return out;
    }

    @Override
    public List<String> lines() {
        return lines(0);
    }
}
