package io.beam.lang.gleam;

/** Sealed Gleam expression IR used by function bodies and codec emitters. */
public sealed interface Expression
    permits RawExpr,
        Variable,
        StringExpr,
        IntegerExpr,
        BoolExpr,
        TupleExpr,
        ListExpr,
        LocalCallExpr,
        RemoteCallExpr,
        ConstructorExpr,
        FieldAccessExpr,
        PipeExpr,
        LetExpr,
        CaseExpr,
        UseExpr,
        BlockExpr {}
