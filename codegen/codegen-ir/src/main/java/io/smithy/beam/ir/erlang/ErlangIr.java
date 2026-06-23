package io.smithy.beam.ir.erlang;

import java.util.List;

public final class ErlangIr {
    private ErlangIr() {}

    public static ErlVar var(String name) {
        return new ErlVar(name);
    }

    public static ErlAtom atom(String value) {
        return new ErlAtom(value);
    }

    public static ErlInteger integer(long value) {
        return new ErlInteger(value);
    }

    public static ErlString string(String value) {
        return new ErlString(value);
    }

    public static ErlBinary binary(String value) {
        return new ErlBinary(value);
    }

    public static ErlCallLocal callLocal(String function, ErlExpr... args) {
        return new ErlCallLocal(function, List.of(args));
    }

    public static ErlCall call(String module, String function, ErlExpr... args) {
        return new ErlCall(new ErlAtom(module), function, List.of(args));
    }

    public static ErlRecordField field(String name, ErlExpr value) {
        return new ErlRecordField(name, value);
    }

    public static ErlRecord record(String name, ErlRecordField... fields) {
        return new ErlRecord(name, null, List.of(fields));
    }

    public static ErlRecord recordUpdate(ErlExpr record, String name, ErlRecordField... fields) {
        return new ErlRecord(name, record, List.of(fields));
    }

    public static ErlGuard guard(String function, ErlExpr... args) {
        return new ErlGuard(function, List.of(args));
    }

    public static ErlAtomPattern atomPattern(String value) {
        return new ErlAtomPattern(value);
    }

    public static ErlVarPattern varPattern(String name) {
        return new ErlVarPattern(name);
    }

    public static ErlIntegerPattern integerPattern(long value) {
        return new ErlIntegerPattern(value);
    }

    public static ErlClause clause(List<ErlPattern> patterns, List<ErlGuard> guards, ErlExpr... body) {
        return new ErlClause(patterns, guards, List.of(body));
    }

    public static ErlClause clause(List<ErlPattern> patterns, ErlExpr... body) {
        return clause(patterns, List.of(), body);
    }

    public static ErlFunctionSpec functionSpec(String name, String inputTypes, String outputTypes) {
        return new ErlFunctionSpec(name, inputTypes, outputTypes);
    }

    public static ErlFunction function(String name, int arity, List<ErlClause> clauses) {
        return new ErlFunction(name, arity, null, clauses);
    }

    public static ErlFunction functionWithSpec(
            String name, int arity, ErlFunctionSpec spec, List<ErlClause> clauses) {
        return new ErlFunction(name, arity, spec, clauses);
    }

    public static ErlFunction functionWithSpec(
            String name,
            int arity,
            String inputTypes,
            String outputTypes,
            List<ErlClause> clauses) {
        return functionWithSpec(name, arity, functionSpec(name, inputTypes, outputTypes), clauses);
    }
}
