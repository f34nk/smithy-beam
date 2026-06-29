package io.smithy.beam.ir.elixir;

public sealed interface ExModuleEntry extends IrObject
    permits ExTypeDef,
        ExDefstruct,
        ExDefexception,
        ExNestedModule,
        ExBlankLine,
        ExComment,
        ExTypedoc,
        ExSourceLine,
        ExFunction {}
