package io.smithy.beam.ir.elixir;

public sealed interface ExPattern extends IrObject
    permits ExAtomPattern,
        ExVarPattern,
        ExNilPattern,
        ExIntegerPattern,
        ExMapPattern,
        ExStructPattern,
        ExListPattern,
        ExConsPattern {}
