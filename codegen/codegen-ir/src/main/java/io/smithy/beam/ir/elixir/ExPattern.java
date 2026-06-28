package io.smithy.beam.ir.elixir;

public sealed interface ExPattern extends IrObject
    permits ExAtomPattern,
        ExBinaryConcatPattern,
        ExVarPattern,
        ExNilPattern,
        ExIntegerPattern,
        ExMapPattern,
        ExStructPattern,
        ExListPattern,
        ExConsPattern,
        ExStringPattern,
        ExTuplePattern {}
