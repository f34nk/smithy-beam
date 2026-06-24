package io.smithy.beam.ir.erlang;

public sealed interface ErlFunctionPreambleEntry extends IrObject
        permits ErlComment, ErlFunctionDoc {}
