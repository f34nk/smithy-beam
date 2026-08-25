package io.beam.dsl.elixir;

public sealed interface Pattern
    permits AtomPattern,
        VariablePattern,
        WildcardPattern,
        TuplePattern,
        ListPattern,
        StringPattern,
        StructPattern,
        PinPattern,
        MapPattern,
        NilPattern,
        ConsListPattern,
        AssignPattern,
        IntegerPattern,
        BinaryPattern,
        ConcatPattern {}
