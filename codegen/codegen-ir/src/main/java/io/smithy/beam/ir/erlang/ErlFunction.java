package io.smithy.beam.ir.erlang;

import java.util.ArrayList;
import java.util.List;

public final class ErlFunction implements IrObject {
    private final String name;
    private final int arity;
    private final List<ErlFunctionPreambleEntry> preamble;
    private final ErlFunctionSpec specOrNull;
    private final List<ErlClause> clauses;

    public ErlFunction(
            String name,
            int arity,
            List<ErlFunctionPreambleEntry> preamble,
            ErlFunctionSpec specOrNull,
            List<ErlClause> clauses) {
        this.name = name;
        this.arity = arity;
        this.preamble = List.copyOf(preamble);
        this.specOrNull = specOrNull;
        this.clauses = List.copyOf(clauses);
    }

    public String name() {
        return name;
    }

    public int arity() {
        return arity;
    }

    public List<ErlFunctionPreambleEntry> preamble() {
        return preamble;
    }

    public ErlFunctionSpec specOrNull() {
        return specOrNull;
    }

    public List<ErlClause> clauses() {
        return clauses;
    }

    public static ErlFunction function(String name, int arity, List<ErlClause> clauses) {
        return new ErlFunction(name, arity, List.of(), null, clauses);
    }

    public static ErlFunction function(
            String name,
            int arity,
            List<ErlFunctionPreambleEntry> preamble,
            ErlFunctionSpec spec,
            List<ErlClause> clauses) {
        return new ErlFunction(name, arity, preamble, spec, clauses);
    }

    public static ErlFunction functionWithSpec(
            String name, int arity, ErlFunctionSpec spec, List<ErlClause> clauses) {
        return new ErlFunction(name, arity, List.of(), spec, clauses);
    }

    public static ErlFunction functionWithDocAndSpec(
            String name,
            int arity,
            ErlFunctionDoc doc,
            ErlFunctionSpec spec,
            List<ErlClause> clauses) {
        return function(name, arity, List.of(doc), spec, clauses);
    }

    public static ErlFunction functionWithSpec(
            String name,
            int arity,
            String inputTypes,
            String outputTypes,
            List<ErlClause> clauses) {
        return functionWithSpec(name, arity, ErlFunctionSpec.functionSpec(name, inputTypes, outputTypes), clauses);
    }

    @Override
    public List<String> lines(int indent) {
        List<String> out = new ArrayList<>();
        for (ErlFunctionPreambleEntry item : preamble) {
            out.addAll(item.lines(indent));
        }
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
