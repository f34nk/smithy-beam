package io.beam.lang.elixir;

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
