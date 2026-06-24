package io.smithy.beam.ir.erlang;

public sealed interface ErlPattern extends IrObject
        permits ErlAtomPattern, ErlVarPattern, ErlIntegerPattern, ErlTuplePattern {}
