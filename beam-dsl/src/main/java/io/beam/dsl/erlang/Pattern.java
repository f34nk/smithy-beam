package io.beam.dsl.erlang;

public sealed interface Pattern
    permits AtomPattern,
        BinaryPattern,
        IntegerPattern,
        ListPattern,
        MapPattern,
        MatchPattern,
        RecordPattern,
        TuplePattern,
        VariablePattern,
        WildcardPattern,
        CatchPattern,
        CharPattern {}
