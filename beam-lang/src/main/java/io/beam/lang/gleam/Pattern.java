package io.beam.lang.gleam;

/** Sealed Gleam pattern IR for let / case / use bindings. */
public sealed interface Pattern
    permits VariablePattern,
        WildcardPattern,
        ConstructorPattern,
        TuplePattern,
        ListPattern,
        StringPattern,
        IntegerPattern {}
