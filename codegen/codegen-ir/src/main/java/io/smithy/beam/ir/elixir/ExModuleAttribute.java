package io.smithy.beam.ir.elixir;

public sealed interface ExModuleAttribute extends IrObject
    permits ExAliasAttr, ExRequireAttr, ExImportAttr, ExImplAttr {}
