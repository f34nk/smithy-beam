package io.smithy.beam.ir.elixir;

public sealed interface ExExpr extends IrObject
    permits ExAtom, ExVar, ExInteger, ExString, ExNil, ExCall, ExCallLocal, ExMap, ExStruct {}
